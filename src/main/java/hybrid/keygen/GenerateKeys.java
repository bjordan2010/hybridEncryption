package hybrid.keygen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;

/**
 * This class is responsible for generating the public and private RSA keys for both, the file producer,
 * and the Client, the file recipient.  The roles are important here.  If the client is the file producer, then
 * this process would need to be changed.
 * See the main method for an example.
 */
public class GenerateKeys
{
	private KeyPairGenerator keyGen;
	private KeyPair pair;
	private PrivateKey privateKey;
	private PublicKey publicKey;

	public GenerateKeys(int keylength) throws NoSuchAlgorithmException, NoSuchProviderException
	{
		this.keyGen = KeyPairGenerator.getInstance("RSA");
		this.keyGen.initialize(keylength);
	}

	public void createKeys()
	{
		this.pair = this.keyGen.generateKeyPair();
		this.privateKey = pair.getPrivate();
		this.publicKey = pair.getPublic();
	}

	public PrivateKey getPrivateKey()
	{
		return this.privateKey;
	}

	public PublicKey getPublicKey()
	{
		return this.publicKey;
	}

	public KeyPair getPair()
	{
		return pair;
	}

	public void writeToFile(String path, byte[] key) throws IOException
	{
		FileOutputStream fos = null;
		try
		{
			File f = new File(path);
			f.getParentFile().mkdirs();

			fos = new FileOutputStream(f);
			fos.write(key);
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
	}

	public static void main(String[] args)
	{
		GenerateKeys gk_Producer; //file producer
		GenerateKeys gk_ClientA; //file recipient

		try
		{
			gk_Producer = new GenerateKeys(1024);
			gk_Producer.createKeys();
			gk_Producer.writeToFile("KeyPair/publicKey_Producer", gk_Producer.getPublicKey().getEncoded());
			gk_Producer.writeToFile("KeyPair/privateKey_Producer", gk_Producer.getPrivateKey().getEncoded());

			gk_ClientA = new GenerateKeys(1024);
			gk_ClientA.createKeys();
			gk_ClientA.writeToFile("KeyPair/publicKey_ClientA", gk_ClientA.getPublicKey().getEncoded());
			gk_ClientA.writeToFile("KeyPair/privateKey_ClientA", gk_ClientA.getPrivateKey().getEncoded());
		}
		catch (Exception e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}
}