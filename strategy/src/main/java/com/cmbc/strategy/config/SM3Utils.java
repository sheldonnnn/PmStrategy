package com.cmbc.strategy.config;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SM3Utils {

    private final static Logger logger = LoggerFactory.getLogger(SM3Utils.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

//    /**
//     * hutool工具加密SALTM
//     *
//     * @param value 待加密值
//     * @param salt 密钥
//     * @return 密文
//     */
//    public static String encryptHtString(String value, String salt) {
//        return SmUtil.sm3(value + salt);
//    }

    /**
     * 传统加密
     *
     * @param value 待加密值
     * @param salt 密钥
     * @return 密文
     */
    public static String encryptString(String value, String salt) {
        String cipherString = null;
        String plainString = value + salt;
        try {
            SM3Digest sm3Digest = new SM3Digest();
            // 初始化计算
            sm3Digest.update(plainString.getBytes(StandardCharsets.UTF_8), 0, plainString.length());
            // 缓存区
            byte[] cipherBytes = new byte[sm3Digest.getDigestSize()];
            sm3Digest.doFinal(cipherBytes, 0);
            // 输出16进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : cipherBytes) {
                sb.append(String.format("%02x", b));
            }
            cipherString = sb.toString();
        } catch (Exception e) {
            logger.error("SM3Utils encrypt failed!");
        }

        return cipherString;
    }

//    public static void main(String[] args) {
//        String authBody = "Z13007001" + "49829202401050000000741498000001";
////        System.out.println(encryptHtString(authBody, "系统分配的密钥"));
//        System.out.println(encryptString(authBody, "I24DJC"));
//    }
}
