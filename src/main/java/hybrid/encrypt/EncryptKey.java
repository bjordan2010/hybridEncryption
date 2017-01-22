package hybrid.encrypt;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

public class EncryptKey
{
	private Cipher cipher;

	public EncryptKey(PublicKey key, File originalKeyFile, File encryptedKeyFile, String cipherAlgorithm)
		throws IOException, GeneralSecurityException
	{
		this.cipher = Cipher.getInstance(cipherAlgorithm);
		encryptFile(getFileInBytes(originalKeyFile), encryptedKeyFile, key);
	}

	public void encryptFile(byte[] input, File output, PublicKey key)
		throws IOException, GeneralSecurityException
	{
		this.cipher.init(Cipher.ENCRYPT_MODE, key);
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

		System.out.println("The key was successfully encrypted and stored in: " + output.getPath());
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