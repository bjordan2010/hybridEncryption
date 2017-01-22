package report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;

/**
 * Created by bjordan on 1/20/17.
 * This class is responsible for creating the invoice in PDF or Excel format.
 * The first thing it does is collect the data and for PDF combine the column names with the data values.
 * Next it uses that data to write the report.  Finally it saves the report to the file system.
 */
public class InvoiceReport
{
	private static BigDecimal balanceDue = new BigDecimal(0);
	private static Integer detailTableY = 627;

	// Combine column description with values of each row
	private static String[][] appendColumnNamesToHeaderData(String[][] a, String[][] b)
	{
		String[][] result = new String[a.length + b.length][];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	// Extract the invoice data for the invoice date and number header table
	private static String[][] extractInvoiceMeta(String[][] header)
	{
		String[][] invoiceMeta = new String[2][2];
		invoiceMeta[0][0] = "Date";       //invoice date literal
		invoiceMeta[1][0] = header[1][2]; //invoice date value
		invoiceMeta[0][1] = "Invoice #";  //invoice no literal
		invoiceMeta[1][1] = header[1][0]; //invoice no value

		return invoiceMeta;
	}

	// Extract the client address data for the header
	private static String[] extractClientAddress(String[][] header)
	{
		String[] clientAddress;

		if (header[1][5] != null)
		{
			clientAddress = new String[5]; // 4 line address
			clientAddress[3] = header[1][5]; //Bill To Address Line 2
			clientAddress[4] = header[1][6] + ", " + header[1][7] + " " + header[1][8]; //City, ST Zip
		}
		else
		{
			clientAddress = new String[4]; // 3 line address
			clientAddress[3] = header[1][6] + ", " + header[1][7] + " " + header[1][8]; //City, ST Zip
		}
		clientAddress[0] = "BILL TO:";
		clientAddress[1] = header[1][3]; //Bill To Name
		clientAddress[2] = header[1][4]; //Bill To Address Line 1

		return clientAddress;
	}

	// extract the due date for the due date header table
	private static String[][] extractDueDate(String[][] header)
	{
		String[][] dueDate = new String[2][1];
		dueDate[0][0] = "Due Date";   //due date literal
		dueDate[1][0] = header[1][1]; //due date value

		return dueDate;
	}

	// Combine the column descriptions with the values for each row
	private static String[][] appendColumnNamesToDetailData(String[][] a, String[][] b)
	{
		String[][] result = new String[a.length + b.length][];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	public static void main(String[] args)
	{
		System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
		Connection connect = null;
		Statement s = null;
		Statement s2 = null;

		// Column headers
		String[][] header = {{"InvoiceNo", "DueDate", "InvoiceDate", "BillToName", "BillToAddress1", "BillToAddress2",
			"BillToCity", "BillToState", "BillToZip", "InvoiceAmount"}};
		String[][] content = {{"Description", "Claims", "Amount"}};

		ResultSet meta = null;
		ResultSet detail = null;

		// Run Queries
		try
		{
			Long invoiceNo = 1L;  //this will be passed in
			Class.forName("com.mysql.jdbc.Driver");
			connect = DriverManager.getConnection("jdbc:mysql://localhost/datanet?user=root&password="+args[0]);
			//connect = DriverManager.getConnection("jdbc:mysql://192.168.50.205/datanet-3-parallel?user=datanet&password="+args[0]);

			s = connect.createStatement();
			s2 = connect.createStatement();

			String sqlMeta = new StringBuffer()
				.append("select invoice.InvoiceNo,invoice.DueDate,invoice.InvoiceDate,invoice.BillToName,invoice.BillToAddress1,")
				.append("invoice.BillToAddress2,invoice.BillToCity,invoice.BillToState,invoice.BillToZip,invoice.InvoiceAmount ")
				.append("from invoice where invoice.InvoiceNo = ")
				.append(invoiceNo).toString();
			String sqlDetail = new StringBuffer()
				.append("select invoiceline.LineDescription as Description, invoiceline.TransactionCount as Claims, ")
				.append("invoiceline.InvoiceAmount as Amount from invoiceline where invoiceline.invoiceNo = ")
				.append(invoiceNo).toString();

			//Retrieve Header Data
			meta = s.executeQuery(sqlMeta);

			//Retrieve Detail Data
			detail = s2.executeQuery(sqlDetail);

			createExcelReport(meta, detail, header, content);
			createPDFReport(meta, detail, header, content);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			// Close
			try
			{
				if (meta != null)
				{
					meta.close();
				}
				if (detail != null)
				{
					detail.close();
				}
				if (s != null)
				{
					s.close();
				}
				if (s2 != null)
				{
					s2.close();
				}
				if (connect != null)
				{
					connect.close();
				}
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
	}

	private static void createExcelReport(ResultSet meta, ResultSet detail, String[][] header, String[][] content)
	{
		//Create Excel Report
		boolean dataFound = false;
		FileOutputStream fileOut = null;
		try
		{
			HSSFWorkbook workbook = new HSSFWorkbook();

			HSSFSheet sheet = workbook.createSheet("invoice");

			//Write header literals and values
			int row = 0;
			HSSFRow rowhead = sheet.createRow((short) row++);
			for (int i = 0; i < header[0].length; i++)
			{
				rowhead.createCell((short) i).setCellValue(header[0][i]);
			}
			if (meta != null)
			{
				while(meta.next())
				{
					HSSFRow arow = sheet.createRow((short) row++);
					for (int i = 0; i < header[0].length; i++)
					{
						arow.createCell((short) i).setCellValue((meta.getString(header[0][i])));
					}
				}
			}
			else
			{
				System.err.println("No header data found.");
				return;
			}

			//Write detail literals and values
			rowhead = sheet.createRow((short) row++);
			for (int i = 0; i < content[0].length; i++)
			{
				rowhead.createCell((short) i).setCellValue(content[0][i]);
			}
			if (detail != null)
			{
				while(detail.next())
				{
					dataFound = true;
					HSSFRow arow = sheet.createRow((short) row++);
					for (int i = 0; i < content[0].length; i++)
					{
						arow.createCell((short) i).setCellValue((detail.getString(content[0][i])));
					}
				}
			}
			else
			{
				System.err.println("No detail data found.");
				return;
			}

			if (dataFound)
			{
				//Total
				HSSFRow arow = sheet.createRow(row);
				arow.createCell((short)0).setCellValue("Total Due This Invoice");
				String formula = "sum(C4:C"+(row++)+")";
				arow.createCell((short)2).setCellFormula(formula);
				arow = sheet.createRow(row);
				arow.createCell((short)0).setCellValue("Balance Due");
				arow.createCell((short)2).setCellFormula(formula);

				fileOut = new FileOutputStream("OriginalFiles/invoice.xls");
				workbook.write(fileOut);

				System.out.println("Excel Created.");
			}
		}
		catch (Exception e)
		{
			System.err.println("Error writing excel file.");
			e.printStackTrace();
		}
		finally
		{
			try
			{
				if (fileOut != null)
				{
					fileOut.close();
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private static void createPDFReport(ResultSet meta, ResultSet detail, String[][] header, String[][] content)
	{
		//Create PDF Report
		try
		{
			if (meta != null)
			{
				if (!meta.next())
				{
					meta.beforeFirst();
				}
				while (meta.next())
				{
					String[][] data = {{meta.getString("InvoiceNo"),
						meta.getString("DueDate"), meta.getString("InvoiceDate"),
						meta.getString("BillToName"), meta.getString("BillToAddress1"),
						meta.getString("BillToAddress2"), meta.getString("BillToCity"),
						meta.getString("BillToState"), meta.getString("BillToZip"),
						meta.getString("InvoiceAmount")}};
					header = appendColumnNamesToHeaderData(header, data);
				}
			}
			else
			{
				System.err.println("No header data found.");
				return;
			}

			if (detail != null)
			{
				if (!detail.next())
				{
					detail.beforeFirst();
				}
				while (detail.next())
				{
					String[][] data = {{detail.getString("Description")
						, detail.getString("Claims")
						, detail.getString("Amount")}};
					content = appendColumnNamesToDetailData(content, data);
				}
			}
			else
			{
				System.err.println("No detail data found.");
				return;
			}

			// Create Document and page
			PDDocument doc = new PDDocument();
			PDPage page = new PDPage();
			doc.addPage(page);

			// Header Image
			BufferedImage image = ImageIO.read( new File( "Images/logo.png" ) );
			PDImageXObject  pdImageXObject = LosslessFactory.createFromImage(doc, image);

			String[][] invoiceMeta = extractInvoiceMeta(header);
			String[][] dueDate = extractDueDate(header);

			PDPageContentStream contentStream = new PDPageContentStream(doc, page);

			// Build Header
			createHeader(contentStream, pdImageXObject, image, page, invoiceMeta, extractClientAddress(header), dueDate);

			// Build Detail table
			drawTable(page, contentStream, detailTableY, 30, content);

			// Totals
			createTotals(contentStream, detailTableY-(40*content.length));

			contentStream.close();

			// Persist document
			doc.save("OriginalFiles/invoice.pdf");

			// Close document
			doc.close();

			System.out.println("PDF Created.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private static void createTotals(PDPageContentStream contentStream, float y) throws IOException
	{
		String[] content = new String[]{"Total Due This Invoice", balanceDue.toString()};
		drawTotalTable(contentStream, y, 368, content);
		content = new String[]{"Balance Due", balanceDue.toString()};
		drawTotalTable(contentStream, y-30, 368, content);
	}

	private static void createHeader(PDPageContentStream contentStream, PDImageXObject pdImageXObject, BufferedImage image,
		PDPage page, String[][] invoiceMeta, String[] clientAddress, String[][] dueDate) throws IOException
	{
		PDFont font = PDType1Font.HELVETICA;

		// RxBenefits Image
		contentStream.drawImage(pdImageXObject, 20, 740, image.getWidth() / 3, image.getHeight() / 3);

		// Invoice Title
		contentStream.beginText();
		contentStream.setFont(font, 18);
		contentStream.newLineAtOffset(527, 747);
		contentStream.showText("Invoice");
		contentStream.endText();

		// RxBenefit Address
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset(50, 732);
		contentStream.showText("P.O. Box 896503");
		contentStream.endText();
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset(50, 718);
		contentStream.showText("Charlotte, NC 28289-6503");
		contentStream.endText();

		// Invoice date and number table
		drawHeaderTable(contentStream, 740, 463, invoiceMeta);

		// Terms
		font = PDType1Font.TIMES_ITALIC;
		contentStream.beginText();
		contentStream.setFont(font, 8);
		contentStream.newLineAtOffset(462, 700);
		contentStream.showText("Terms: Net 7 Upon Receipt of Invoice");
		contentStream.endText();

		// Client Address
		font = PDType1Font.HELVETICA;
		int y = 690;
		for (String addressLine : clientAddress)
		{
			contentStream.beginText();
			contentStream.setFont(font, 11);
			contentStream.newLineAtOffset(30, y);
			contentStream.showText(addressLine);
			contentStream.endText();
			y = y - 14;
		}

		// Due Date
		drawHeaderTable(contentStream, 663, 523, dueDate);
	}

	private static void drawTable(PDPage page, PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException
	{
		final int rows = content.length;
		final int cols = content[0].length;
		final float rowHeight = 20f;
		final float tableWidth = page.getMediaBox().getWidth() - (2 * margin);
		final float tableHeight = rowHeight * rows;
		final float colWidth = tableWidth / (float) cols;
		final float cellMargin = 2f;

		//Shade the header row
		contentStream.setNonStrokingColor(224, 224, 224); //light grey
		contentStream.addRect(30, detailTableY, page.getMediaBox().getWidth() - (2 * 30f), -20f);
		contentStream.fill();

		contentStream.setNonStrokingColor(0, 0, 0); //back to black
		// draw the rows
		float nexty = y;
		for (int i = 0; i <= rows; i++)
		{
			contentStream.moveTo(margin, nexty);
			contentStream.lineTo(margin + tableWidth, nexty);
			contentStream.stroke();
			nexty -= rowHeight;
		}

		// draw the columns
		float nextx = margin;
		for (int i = 0; i <= cols; i++)
		{
			contentStream.moveTo(nextx, y);
			contentStream.lineTo(nextx, y - tableHeight);
			contentStream.stroke();
			nextx += colWidth;
		}

		contentStream.setFont(PDType1Font.HELVETICA_BOLD, 8);

		float textx = margin + cellMargin;
		float texty = y - 15;
		for (int i = 0; i < content.length; i++)
		{
			String[] aContent = content[i];
			for (int j = 0; j < aContent.length; j++)
			{
				String text = aContent[j];

				if (text != null)
				{
					contentStream.beginText();
					contentStream.newLineAtOffset(textx, texty);
					if (i > 0 && j == 2)
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
						balanceDue = balanceDue.add(new BigDecimal(text));
					}
					else
					{
						contentStream.showText(text);
					}
					contentStream.endText();
				}
				textx += colWidth;
			}

			texty -= rowHeight;
			textx = margin + cellMargin;
		}
	}

	private static void drawHeaderTable(PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException
	{
		final int rows = content.length;
		final int cols = content[0].length;
		final float rowHeight = 15f;
		final float tableWidth = 60f * cols;
		final float tableHeight = rowHeight * rows;
		final float colWidth = tableWidth / (float) cols;
		final float cellMargin = 4f;

		// draw the rows
		float nexty = y;
		for (int i = 0; i <= rows; i++)
		{
			contentStream.moveTo(margin, nexty);
			contentStream.lineTo(margin + tableWidth, nexty);
			contentStream.stroke();
			nexty -= rowHeight;
		}

		// draw the columns
		float nextx = margin;
		for (int i = 0; i <= cols; i++)
		{
			contentStream.moveTo(nextx, y);
			contentStream.lineTo(nextx, y - tableHeight);
			contentStream.stroke();
			nextx += colWidth;
		}

		contentStream.setFont(PDType1Font.HELVETICA, 10);

		float textx = margin + cellMargin;
		float texty = y - 10;
		for (String[] aContent : content)
		{
			for (String text : aContent)
			{
				if (text != null)
				{
					contentStream.beginText();
					contentStream.newLineAtOffset(textx, texty);
					contentStream.showText(text);
					contentStream.endText();
				}
				textx += colWidth;
			}

			texty -= rowHeight;
			textx = margin + cellMargin;
		}
	}

	private static void drawTotalTable(PDPageContentStream contentStream, float y, float margin,
		String[] content) throws IOException
	{
		final int rows = 1;
		final float rowHeight = 15f;
		final float tableWidth = 215f;
		final float tableHeight = rowHeight * rows;
		final float cellMargin = 4f;

		// draw the row
		float nexty = y;
		contentStream.moveTo(margin, nexty);
		contentStream.lineTo(margin + tableWidth, nexty);
		contentStream.stroke();
		nexty -= rowHeight;
		contentStream.moveTo(margin, nexty);
		contentStream.lineTo(margin + tableWidth, nexty);
		contentStream.stroke();

		// draw the column
		float nextx = margin;
		contentStream.moveTo(nextx, y);
		contentStream.lineTo(nextx, y - tableHeight);
		contentStream.stroke();
		nextx += tableWidth;
		contentStream.moveTo(nextx, y);
		contentStream.lineTo(nextx, y - tableHeight);
		contentStream.stroke();

		contentStream.setFont(PDType1Font.HELVETICA, 8);

		float textx = margin + cellMargin;
		float texty = y - 10;
		for (int i = 0; i < content.length; i++)
		{
			String text = content[i];
			if (text != null)
			{
				contentStream.beginText();
				if (i > 0)
				{

					textx = (margin + tableWidth) - ((text.length() * 5) + cellMargin);
					contentStream.newLineAtOffset(textx, texty);
					contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
				}
				else
				{
					contentStream.newLineAtOffset(textx, texty);
					contentStream.showText(text);
				}
				contentStream.endText();

			}
		}
	}
}
