#!/usr/bin/env node
/**
 * Compiles firestore.rules using the official Firebase Firestore emulator compiler
 * and fails with a non-zero exit code if compilation fails or if the stripped
 * compiled RulesetAst exceeds MAX_COMPILED_BYTES (default: 230,000 bytes, safely
 * below Firebase's 256,000-byte compiled ruleset deployment limit).
 */

import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";

const RULES_PATH = path.resolve(process.argv[2] || "firestore.rules");
const LIMIT_BYTES = Number.parseInt(process.env.MAX_RULES_BYTES || process.argv[3] || "230000", 10);
const FIREBASE_HARD_LIMIT = 256000;

function findEmulatorJar() {
  if (process.env.FIRESTORE_EMULATOR_JAR && fs.existsSync(process.env.FIRESTORE_EMULATOR_JAR)) {
    return process.env.FIRESTORE_EMULATOR_JAR;
  }
  const searchDirs = [
    process.env.FIREBASE_EMULATORS_PATH,
    "/opt/firebase-emulators",
    path.join(os.homedir(), ".cache", "firebase", "emulators"),
  ].filter(Boolean);
  for (const dir of searchDirs) {
    if (!fs.existsSync(dir)) continue;
    const jars = fs
      .readdirSync(dir)
      .filter((f) => /^cloud-firestore-emulator-.*\.jar$/i.test(f))
      .sort()
      .reverse();
    if (jars.length > 0) {
      return path.join(dir, jars[0]);
    }
  }
  throw new Error(
    "Could not find cloud-firestore-emulator-*.jar in /opt/firebase-emulators or ~/.cache/firebase/emulators. Run: npx --yes firebase-tools@latest setup:emulators:firestore"
  );
}

const JAVA_SOURCE = `
import com.google.firebase.rules.lang.FirebaseRulesProtoCompiler;
import com.google.firebase.rules.lang.common.FirebaseRulesLangOptions;
import com.google.firebase.rules.v1.Source;
import com.google.firebase.rules.v1.File;
import com.google.firebase.rules.v1.Issue;
import com.google.firebase.rules.runtime.v1.RulesetAst;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RulesSizeCheck {
    @SuppressWarnings("unchecked")
    static <T extends Message> T stripMeta(T msg) {
        Message.Builder b = msg.toBuilder();
        for (FieldDescriptor fd : msg.getDescriptorForType().getFields()) {
            String name = fd.getName();
            if (name.equals("source_position") || name.equals("file_metadata")) {
                b.clearField(fd);
                continue;
            }
            if (fd.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                if (fd.isRepeated()) {
                    int n = b.getRepeatedFieldCount(fd);
                    for (int i = 0; i < n; i++) {
                        Message item = (Message) b.getRepeatedField(fd, i);
                        b.setRepeatedField(fd, i, stripMeta(item));
                    }
                } else if (b.hasField(fd)) {
                    Message sub = (Message) b.getField(fd);
                    b.setField(fd, stripMeta(sub));
                }
            }
        }
        return (T) b.build();
    }

    public static void main(String[] args) throws Exception {
        String rulesPath = args[0];
        String content = Files.readString(Paths.get(rulesPath));
        Source src = Source.newBuilder()
            .addFiles(File.newBuilder().setName(rulesPath).setContent(content))
            .build();
        var compiler = FirebaseRulesProtoCompiler.createStandalone(
            FirebaseRulesLangOptions.builder().allowTernaryOperator(true).build()
        );
        var res = compiler.compile(src);
        int errs = 0;
        for (Issue issue : res.getIssues()) {
            if (issue.getSeverity() == Issue.Severity.ERROR) errs++;
            System.err.println(issue.getSeverity() + " line " + issue.getSourcePosition().getLine() + ": " + issue.getDescription());
        }
        RulesetAst ast = res.getRulesetAst();
        RulesetAst stripped = stripMeta(ast);
        System.out.println(content.getBytes("UTF-8").length + " " + ast.getSerializedSize() + " " + stripped.getSerializedSize() + " " + errs);
        if (errs > 0) System.exit(2);
    }
}
`;

function main() {
  if (!fs.existsSync(RULES_PATH)) {
    console.error(`ERROR: Rules file not found: ${RULES_PATH}`);
    process.exit(1);
  }
  const jarPath = findEmulatorJar();
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "rules-size-check-"));
  try {
    const javaFile = path.join(tmpDir, "RulesSizeCheck.java");
    fs.writeFileSync(javaFile, JAVA_SOURCE, "utf8");
    execFileSync("javac", ["-proc:none", "-cp", jarPath, javaFile], { stdio: "inherit" });
    const out = execFileSync(
      "java",
      ["-cp", `${tmpDir}${path.delimiter}${jarPath}`, "RulesSizeCheck", RULES_PATH],
      { encoding: "utf8" }
    ).trim();

    const [sourceBytes, fullAstBytes, strippedAstBytes, errCount] = out.split(/\s+/).map(Number);
    console.log(
      `[rules-size-guard] file=${path.basename(RULES_PATH)} compiler=${path.basename(jarPath)} source=${sourceBytes}B fullAst=${fullAstBytes}B strippedAst=${strippedAstBytes}B guardLimit=${LIMIT_BYTES}B firebaseLimit=${FIREBASE_HARD_LIMIT}B errors=${errCount}`
    );

    if (errCount > 0) {
      console.error(`FAIL: ${RULES_PATH} has ${errCount} compile error(s).`);
      process.exit(2);
    }
    if (strippedAstBytes > LIMIT_BYTES) {
      console.error(
        `FAIL: Compiled rules AST (${strippedAstBytes} bytes) exceeds CI guard limit (${LIMIT_BYTES} bytes; Firebase hard limit is ${FIREBASE_HARD_LIMIT} bytes).`
      );
      process.exit(3);
    }
    console.log(
      `OK: Compiled rules AST (${strippedAstBytes} bytes) is ${LIMIT_BYTES - strippedAstBytes} bytes under guard limit (${LIMIT_BYTES} bytes) and ${FIREBASE_HARD_LIMIT - strippedAstBytes} bytes under Firebase hard limit (${FIREBASE_HARD_LIMIT} bytes).`
    );
  } finally {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  }
}

main();
