package hybrid.decrypt;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class DecryptData
{
	private Cipher cipher;

	public DecryptData(File encryptedFileReceived, File decryptedFile, SecretKeySpec secretKey, String algorithm)
		throws IOException, GeneralSecurityException
	{
		this.cipher = Cipher.getInstance(algorithm);
		decryptFile(getFileInBytes(encryptedFileReceived), decryptedFile, secretKey);
	}

	public void decryptFile(byte[] input, File output, SecretKeySpec key)
		throws IOException, GeneralSecurityException
	{
		this.cipher.init(Cipher.DECRYPT_MODE, key);
		writeToFile(output, this.cipher.doFinal(input));
	}

	private void writeToFile(File output, byte[] toWrite)
		throws IllegalBlockSizeException, BadPaddingException, IOException
	{
		FileOutputStream fos = null;
		try
		{
			output.getParentFile().mkdirs();
			fos = new FileOutputStream(output);
			fos.write(toWrite);
			fos.flush();
		}
		catch (Exception e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			if (fos != null)
			{
				fos.close();
			}
		}

		System.out.println("The file was successfully decrypted. You can view it in: " + output.getPath());
	}

	public byte[] getFileInBytes(File f) throws IOException
	{
		FileInputStream fis = new FileInputStream(f);
		byte[] fbytes = new byte[(int) f.length()];
		fis.read(fbytes);
		fis.close();
		return fbytes;
	}
}