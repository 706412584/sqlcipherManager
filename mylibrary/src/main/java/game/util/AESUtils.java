package game.util;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtils {
	/* 将算法改为 AES/GCM/NoPadding */
	private static final String CipherMode = "AES/GCM/NoPadding";
	
	/* GCM认证标签长度（位），通常为128位 */
	private static final int GCM_TAG_LENGTH = 128;
	/* GCM模式要求的IV长度（字节），通常为12字节 */
	private static final int GCM_IV_LENGTH = 12;
	
	/* 创建密钥 (保持不变，但建议确保密钥长度为128位/16字节) */
	private static SecretKeySpec createKey(String password) {
		byte[] data = null;
		if (password == null) {
			password = "";
		}
		StringBuffer sb = new StringBuffer(32);
		sb.append(password);
		while (sb.length() < 32) {
			sb.append("0");
		}
		if (sb.length() > 32) {
			sb.setLength(32);
		}
		
		try {
			data = sb.toString().getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// 注意：GCM模式通常使用128位密钥。如果密钥材料过长，
		// 应考虑使用更规范的方式（如PBKDF2）派生固定长度密钥。
		return new SecretKeySpec(data, "AES");
	}
	
	/* 加密字节数据 - 核心改动 */
	public static byte[] encrypt(byte[] content, String password) {
		try {
			SecretKeySpec key = createKey(password);
			Cipher cipher = Cipher.getInstance(CipherMode);
			
			// **关键改动1：生成随机IV（每次加密不同）**
			SecureRandom secureRandom = new SecureRandom();
			byte[] iv = new byte[GCM_IV_LENGTH];
			secureRandom.nextBytes(iv);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			
			cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
			byte[] encryptedData = cipher.doFinal(content);
			
			// **关键改动2：将IV拼接到密文前**
			// 最终输出格式为：IV (12字节) + 实际密文
			byte[] combined = new byte[GCM_IV_LENGTH + encryptedData.length];
			System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
			System.arraycopy(encryptedData, 0, combined, GCM_IV_LENGTH, encryptedData.length);
			
			return combined;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/* 加密(结果为16进制字符串) - 方法签名不变，内部调用已修改的encrypt方法 */
	public static String encrypt(String content, String password) {
		byte[] data = null;
		try {
			data = content.getBytes("UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		data = encrypt(data, password);
		String result = byte2hex(data);
		return result;
	}
	
	/* 解密字节数组 - */
	public static byte[] decrypt(byte[] content, String password) {
		try {
			// **关键改动3：从输入数据中分离出IV和密文**
			if (content.length < GCM_IV_LENGTH) {
				throw new IllegalArgumentException("加密数据太短，不包含有效的IV");
			}
			byte[] iv = Arrays.copyOfRange(content, 0, GCM_IV_LENGTH);
			byte[] cipherText = Arrays.copyOfRange(content, GCM_IV_LENGTH, content.length);
			
			SecretKeySpec key = createKey(password);
			Cipher cipher = Cipher.getInstance(CipherMode);
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
			
			cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
			byte[] result = cipher.doFinal(cipherText); // 此处会验证认证标签
			return result;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/* 解密16进制的字符串为字符串 - 方法签名不变，内部调用已修改的decrypt方法 */
	public static String decrypt(String content, String password) {
		byte[] data = null;
		try {
			data = hex2byte(content);
		} catch (Exception e) {
			e.printStackTrace();
		}
		data = decrypt(data, password);
		if (data == null) return null;
		String result = null;
		try {
			result = new String(data, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	// byte2hex 和 hex2byte 方法保持不变
	public static String byte2hex(byte[] b) {
		StringBuffer sb = new StringBuffer(b.length * 2);
		String tmp = "";
		for (int n = 0; n < b.length; n++) {
			tmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
			if (tmp.length() == 1) {
				sb.append("0");
			}
			sb.append(tmp);
		}
		return sb.toString().toUpperCase();
	}
	
	private static byte[] hex2byte(String inputString) {
		if (inputString == null || inputString.length() < 2) {
			return new byte[0];
		}
		inputString = inputString.toLowerCase();
		int l = inputString.length() / 2;
		byte[] result = new byte[l];
		for (int i = 0; i < l; ++i) {
			String tmp = inputString.substring(2 * i, 2 * i + 2);
			result[i] = (byte) (Integer.parseInt(tmp, 16) & 0xFF);
		}
		return result;
	}
}
