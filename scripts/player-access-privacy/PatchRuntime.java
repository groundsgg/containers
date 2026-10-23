package gg.grounds.build.privacy;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Build-time, fail-closed patch of one known logging operand; no runtime instrumentation. */
public final class PatchRuntime {
    private static final String HELPER = "gg/grounds/runtime/logging/PlayerAccessLogPrivacy";
    private static final String VERSION_METHOD = "groundsPlayerAccessLogPrivacyVersion";
    private static final ClassDesc HELPER_DESC = ClassDesc.ofInternalName(HELPER);

    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("runtime input output helperClass expectedInputSha256 required");
        String runtime = args[0];
        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        byte[] helper = Files.readAllBytes(Path.of(args[3]));
        String expected = args[4];
        if (!expected.matches("[0-9a-f]{64}") || !digest(Files.readAllBytes(input)).equals(expected)) {
            throw new IllegalArgumentException("Upstream runtime checksum mismatch");
        }
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Input and output must differ");
        }
        String target = switch (runtime) {
            case "velocity" -> "com/velocitypowered/proxy/connection/client/ConnectedPlayer";
            case "geyser" -> "org/geysermc/geyser/platform/standalone/GeyserStandaloneLogger";
            default -> throw new IllegalArgumentException("Unknown runtime");
        };
        ClassFile classFile = ClassFile.of();
        ClassModel helperModel = classFile.parse(helper);
        if (!helperModel.thisClass().asInternalName().equals(HELPER)) throw new IllegalArgumentException("Unexpected helper class");

        try (ZipFile jar = new ZipFile(input.toFile())) {
            ZipEntry targetEntry = jar.getEntry(target + ".class");
            if (targetEntry == null) throw new IllegalArgumentException("Expected executable runtime class is missing");
            if (jar.stream().anyMatch(e -> e.getName().startsWith("META-INF/") && e.getName().matches(".*\\.(SF|RSA|DSA)"))) {
                throw new IllegalArgumentException("Refusing to modify a signed runtime");
            }
            if (jar.getEntry(HELPER + ".class") != null) throw new IllegalArgumentException("Runtime is already or partially patched");
            byte[] original = jar.getInputStream(targetEntry).readAllBytes();
            ClassModel model = classFile.parse(original);
            if (!model.thisClass().asInternalName().equals(target)) throw new IllegalArgumentException("Unexpected class owner");
            if (model.methods().stream().anyMatch(m -> m.methodName().equalsString(VERSION_METHOD))) throw new IllegalArgumentException("Runtime already or partially patched");
            String targetMethod = runtime.equals("velocity") ? "disconnect0" : "info";
            String targetDescriptor = runtime.equals("velocity") ? "(Lnet/kyori/adventure/text/Component;Z)V" : "(Ljava/lang/String;)V";
            String loggerOwner = runtime.equals("velocity") ? "net/kyori/adventure/text/logger/slf4j/ComponentLogger" : "org/slf4j/Logger";
            String loggerDescriptor = runtime.equals("velocity") ? "(Lnet/kyori/adventure/text/Component;)V" : "(Ljava/lang/String;)V";
            ClassDesc operand = ClassDesc.of(runtime.equals("velocity") ? "net.kyori.adventure.text.Component" : "java.lang.String");
            int[] matched = new int[2];
            byte[] patched = classFile.transformClass(model, ClassTransform.endHandler(builder ->
                builder.withMethodBody(VERSION_METHOD, MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_int),
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.iconst_1().ireturn())
            ).andThen((builder, element) -> {
                if (element instanceof MethodModel method && method.methodName().equalsString(targetMethod)
                    && method.methodType().equalsString(targetDescriptor)) {
                    matched[0]++;
                    builder.transformMethod(method, MethodTransform.transformingCode((code, instruction) -> {
                        if (instruction instanceof InvokeInstruction invoke
                            && invoke.owner().asInternalName().equals(loggerOwner)
                            && invoke.name().equalsString("info") && invoke.type().equalsString(loggerDescriptor)) {
                            matched[1]++;
                            code.invokestatic(HELPER_DESC, "forLog", MethodTypeDesc.of(operand, operand));
                        }
                        code.with(instruction);
                    }));
                } else builder.with(element);
            }));
            if (matched[0] != 1 || matched[1] != 1) throw new IllegalArgumentException("Unexpected logging method/invocation shape: " + Arrays.toString(matched));
            if (!classFile.verify(patched).isEmpty()) throw new IllegalArgumentException("Patched class verification failed");
            Files.createDirectories(output.toAbsolutePath().getParent());
            Path staged = Files.createTempFile(output.toAbsolutePath().getParent(), "privacy-", ".jar");
            boolean published = false;
            try {
                try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(staged))) {
                    var entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        ZipEntry copy = new ZipEntry(entry.getName());
                        copy.setTime(entry.getTime());
                        out.putNextEntry(copy);
                        if (!entry.isDirectory()) out.write(entry.getName().equals(target + ".class") ? patched : jar.getInputStream(entry).readAllBytes());
                        out.closeEntry();
                    }
                    ZipEntry helperEntry = new ZipEntry(HELPER + ".class");
                    helperEntry.setTime(0);
                    out.putNextEntry(helperEntry);
                    out.write(helper);
                    out.closeEntry();
                }
                Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                published = true;
            } finally { if (!published) Files.deleteIfExists(staged); }
            System.out.println(runtime + " privacy boundary verified and patched (one logging operand)");
        }
    }

    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
