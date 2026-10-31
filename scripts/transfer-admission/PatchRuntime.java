package gg.grounds.build.transfer;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Exact pinned bytecode anchors; fails the build on upstream drift or partial application. */
public final class PatchRuntime {
    private static final String BASE = "com/velocitypowered/proxy/";
    private static final ClassDesc CONNECTION = ClassDesc.ofInternalName(BASE + "connection/MinecraftConnection");
    private static final ClassDesc HELPER = ClassDesc.of("gg.grounds.runtime.transfer.TransferAdmission");
    private static final ClassDesc HANDLER = ClassDesc.ofInternalName(BASE + "connection/client/InitialLoginSessionHandler");
    private static final ClassDesc LOGIN = ClassDesc.ofInternalName(BASE + "protocol/packet/ServerLoginPacket");
    private static final ClassDesc COOKIE = ClassDesc.ofInternalName(BASE + "protocol/packet/ServerboundCookieResponsePacket");
    private static final String MARKER = "groundsTransferAdmissionVersion";
    private static String internal(ClassDesc desc) { return desc.descriptorString().substring(1, desc.descriptorString().length() - 1); }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]), helpers = Path.of(args[3]);
        if (input.toAbsolutePath().equals(output.toAbsolutePath())) throw new IllegalArgumentException("Distinct input/output required");
        if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input))).equals(args[2]))
            throw new IllegalArgumentException("Runtime checksum mismatch");
        ClassFile cf = ClassFile.of();
        Map<String, byte[]> replacements = new HashMap<>();
        try (ZipFile jar = new ZipFile(input.toFile())) {
            if (jar.stream().anyMatch(e -> e.getName().matches("META-INF/.*\\.(SF|RSA|DSA)") || e.getName().startsWith("gg/grounds/runtime/transfer/")))
                throw new IllegalArgumentException("Signed or already patched runtime");
            String handshake = BASE + "connection/client/HandshakeSessionHandler";
            for (String name : List.of(handshake, internal(HANDLER))) {
                ClassModel model = cf.parse(jar.getInputStream(jar.getEntry(name + ".class")).readAllBytes());
                if (model.methods().stream().anyMatch(m -> m.methodName().equalsString(MARKER))) throw new IllegalArgumentException("Already patched");
                int[] matched = new int[2];
                byte[] patched = cf.transformClass(model, (builder, element) -> {
                    if (element instanceof MethodModel method && name.equals(handshake) && method.methodName().equalsString("handleLogin")
                        && method.methodType().equalsString("(L" + BASE + "protocol/packet/HandshakePacket;L" + BASE + "connection/client/InitialInboundConnection;)V")) {
                        matched[0]++;
                        builder.transformMethod(method, MethodTransform.transformingCode((code, instruction) -> {
                            if (instruction instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals(BASE + "util/ratelimit/Ratelimiter")
                                && invoke.name().equalsString("attempt") && invoke.type().equalsString("(Ljava/lang/Object;)Z")) {
                                matched[1]++;
                                code.aload(0).getfield(ClassDesc.ofInternalName(name), "connection", CONNECTION).aload(1)
                                    .invokestatic(HELPER, "attempt", MethodTypeDesc.of(ConstantDescs.CD_boolean,
                                        ClassDesc.ofInternalName(BASE + "util/ratelimit/Ratelimiter"), ConstantDescs.CD_Object,
                                        CONNECTION, ClassDesc.ofInternalName(BASE + "protocol/packet/HandshakePacket")));
                            } else code.with(instruction);
                        }));
                    } else if (element instanceof MethodModel method && name.equals(internal(HANDLER)) && method.methodName().equalsString("handle")
                        && method.methodType().equalsString("(L" + internal(LOGIN) + ";)Z")) {
                        matched[0]++;
                        builder.transformMethod(method, MethodTransform.transformingCode(new CodeTransform() {
                            public void accept(CodeBuilder code, CodeElement instruction) { code.with(instruction); }
                            public void atStart(CodeBuilder code) {
                            matched[1]++;
                            Label original = code.newLabel();
                            code.aload(0).getfield(HANDLER, "mcConnection", CONNECTION).aload(0).aload(1)
                                .invokestatic(HELPER, "begin", MethodTypeDesc.of(ConstantDescs.CD_boolean, CONNECTION, HANDLER, LOGIN))
                                .ifeq(original).iconst_1().ireturn().labelBinding(original);
                            }
                        }));
                    } else builder.with(element);
                });
                if (!Arrays.equals(matched, new int[]{1, 1})) throw new IllegalArgumentException("Unexpected anchor shape " + name + Arrays.toString(matched));
                ClassModel intermediate = cf.parse(patched);
                if (name.equals(internal(HANDLER)) && intermediate.methods().stream().anyMatch(m -> m.methodName().equalsString("handle") && m.methodType().equalsString("(L" + internal(COOKIE) + ";)Z")))
                    throw new IllegalArgumentException("Upstream cookie handler changed");
                patched = cf.transformClass(intermediate, ClassTransform.endHandler(builder -> {
                    builder.withMethodBody(MARKER, MethodTypeDesc.of(ConstantDescs.CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.iconst_1().ireturn());
                    if (name.equals(internal(HANDLER))) builder.withMethodBody("handle", MethodTypeDesc.of(ConstantDescs.CD_boolean, COOKIE), ClassFile.ACC_PUBLIC,
                        code -> code.aload(0).getfield(HANDLER, "mcConnection", CONNECTION).aload(1)
                            .invokestatic(HELPER, "cookie", MethodTypeDesc.of(ConstantDescs.CD_boolean, CONNECTION, COOKIE)).ireturn());
                }));
                if (!cf.verify(patched).isEmpty()) throw new IllegalArgumentException("Invalid patched class: " + cf.verify(patched));
                replacements.put(name + ".class", patched);
            }
            Files.createDirectories(output.toAbsolutePath().getParent());
            Path staged = Files.createTempFile(output.toAbsolutePath().getParent(), "transfer-", ".jar");
            try {
                try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(staged))) {
                    for (var entry : Collections.list(jar.entries())) {
                        ZipEntry copy = new ZipEntry(entry.getName()); copy.setTime(entry.getTime()); out.putNextEntry(copy);
                        if (!entry.isDirectory()) out.write(replacements.containsKey(entry.getName()) ? replacements.get(entry.getName()) : jar.getInputStream(entry).readAllBytes());
                        out.closeEntry();
                    }
                    try (var files = Files.list(helpers)) {
                        for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                            ZipEntry entry = new ZipEntry("gg/grounds/runtime/transfer/" + file.getFileName()); entry.setTime(0);
                            out.putNextEntry(entry); out.write(Files.readAllBytes(file)); out.closeEntry();
                        }
                    }
                }
                Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(staged); }
        }
        System.out.println("Early transfer admission: two exact runtime anchors verified");
    }
}
