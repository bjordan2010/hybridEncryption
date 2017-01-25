package report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.FillPatternType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by bjordan on 1/20/17.
 * This class is responsible for creating the claim summary in PDF or Excel format.
 */
public class ClaimSummaryReport
{
	private static BigDecimal grandTotalCount = new BigDecimal(0);
	private static BigDecimal grandTotalAmount = new BigDecimal(0);
	private static BigDecimal grandTotalFee = new BigDecimal(0);
	private static Integer detailTableY = 667;

	// Combine column description with values of each row
	private static String[][] appendColumnNamesToHeaderData(String[][] a, String[][] b)
	{
		String[][] result = new String[a.length + b.length][];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
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
		String[][] header = {{"BillToName", "InvoiceDate", "DateFrom", "DateTo"}};
		String[][] content = {{"Carrier", "Group Number", "Claim Count",
			"Claim Amount", "Transaction Fee", "Total"}};

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
				.append("select invoice.BillToName, invoice.InvoiceDate, min(transactionbillingperiod.DateFrom) as DateFrom, ")
				.append("max(transactionbillingperiod.DateTo) as DateTo ")
				.append("from invoice ")
				.append("left outer join transactionBillingPeriod on transactionBillingPeriod.invoicePeriodNo = invoice.invoicePeriodNo ")
				.append("where invoice.invoiceNo = ")
				.append(invoiceNo).toString();
			String sqlDetail = new StringBuffer()
				.append("select vendorContract.contractNumber as Carrier, benefitplan.groupNumber as `Group Number`, transactionBillingSummary.transactionCount as `Claim Count`, ")
				.append("transactionBillingSummary.transactionAmount as `Claim Amount`, transactionBillingSummary.transactionFee as `Transaction Fee`, ")
				.append("transactionBillingSummary.transactionAmount+transactionBillingSummary.transactionFee as Total " )
				.append("from invoice ")
				.append("left outer join transactionBillingPeriod on transactionBillingPeriod.invoicePeriodNo = invoice.invoicePeriodNo ")
				.append("left outer join transactionBillingSummary on transactionBillingSummary.transactionBillingPeriodNo = transactionBillingPeriod.transactionBillingPeriodNo ")
				.append("left outer join vendorContract on vendorContract.vendorContractNo = transactionBillingSummary.vendorContractNo ")
				.append("left outer join benefitPlan on benefitPlan.benefitPlanNo = transactionBillingSummary.benefitPlanNo ")
				.append("where invoice.invoiceNo = ")
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

			HSSFSheet sheet = workbook.createSheet("claim_summary");
			sheet.setColumnWidth(0, 3072);  //12
			sheet.setColumnWidth(1, 4096);  //16
			sheet.setColumnWidth(2, 3072);  //12
			sheet.setColumnWidth(3, 3840);  //15
			sheet.setColumnWidth(4, 3840);  //15

			//Place header literals and values
			int row = 0;
			int cell = 0;
			if (meta.next())
			{
				HSSFRow rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell).setCellValue("RxBenefits");
				rowhead.createCell((short) cell + 4).setCellValue("For:   " + meta.getString(header[0][0]));
				rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell).setCellValue("P.O. Box 896503");
				rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell).setCellValue("Charlotte, NC 28289-6503");
				rowhead.createCell((short) cell + 4).setCellValue("Invoice Date:   " + new SimpleDateFormat("MM/dd/yyyy")
					.format(new SimpleDateFormat("yyyy-MM-dd").parse(meta.getString(header[0][1]))));
				rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell).setCellValue("1-800-334-8134");
				rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell + 4).setCellValue("Period Covered");
				row++;
				rowhead = sheet.createRow((short) row++);
				String period = new SimpleDateFormat("MM/dd/yyyy").format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(meta.getString(header[0][2]))) + " to " +
					new SimpleDateFormat("MM/dd/yyyy").format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(meta.getString(header[0][3])));
				rowhead.createCell((short) cell+4).setCellValue(period);
				rowhead = sheet.createRow((short) row++);
				rowhead.createCell((short) cell).setCellValue("Administrator of:  Pharmacy Programs");
				row++;
			}
			else
			{
				System.err.println("No header data found.");
				return;
			}

			//Write detail literals and values
			HSSFCellStyle style = workbook.createCellStyle();
			style.setFillPattern((short) FillPatternType.FINE_DOTS.ordinal());
			style.setFillBackgroundColor(HSSFColor.GREY_25_PERCENT.index);
			style.setAlignment(HSSFCellStyle.ALIGN_CENTER);
			style.setBorderBottom(HSSFCellStyle.BORDER_MEDIUM);
			style.setBorderRight(HSSFCellStyle.BORDER_MEDIUM);
			style.setBorderTop(HSSFCellStyle.BORDER_MEDIUM);
			style.setBorderLeft(HSSFCellStyle.BORDER_MEDIUM);
			HSSFRow rowhead = sheet.createRow((short) row++);
			for (int i = 0; i < content[0].length; i++)
			{
				HSSFCell acell = rowhead.createCell((short) i);
				acell.setCellValue(content[0][i]);
				acell.setCellStyle(style);
			}
			if (detail != null)
			{
				while(detail.next())
				{
					dataFound = true;
					HSSFRow arow = sheet.createRow((short) row++);
					for (int i = 0; i < content[0].length; i++)
					{
						style = workbook.createCellStyle();
						style.setAlignment(HSSFCellStyle.ALIGN_CENTER);
						if (i > 1 && i < 6)
						{
							if (i > 2)
							{
								style.setDataFormat((short) 8); //currency
							}
							HSSFCell acell = arow.createCell((short) i, 0);
							acell.setCellStyle(style);
							acell.setCellValue((detail.getDouble(content[0][i])));
						}
						else
						{
							HSSFCell acell = arow.createCell((short) i);
							acell.setCellStyle(style);
							acell.setCellValue((detail.getString(content[0][i])));
						}
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
				row++;
				style = workbook.createCellStyle();
				style.setAlignment(HSSFCellStyle.ALIGN_CENTER);
				HSSFRow arow = sheet.createRow((short)row);
				HSSFCell acell = arow.createCell((short)1);
				acell.setCellStyle(style);
				acell.setCellValue("Grand Total");
				String formula = "sum(C4:C"+(row)+")";
				acell = arow.createCell((short)2);
				acell.setCellStyle(style);
				acell.setCellFormula(formula);
				formula = "sum(D4:D"+(row)+")";
				acell = arow.createCell((short)3);
				style = workbook.createCellStyle();
				style.setAlignment(HSSFCellStyle.ALIGN_CENTER);
				style.setDataFormat((short) 8);  //currency
				acell.setCellStyle(style);
				acell.setCellFormula(formula);
				formula = "sum(E4:E"+(row)+")";
				acell = arow.createCell((short)4);
				acell.setCellStyle(style);
				acell.setCellFormula(formula);

				fileOut = new FileOutputStream("OriginalFiles/claim_summary.xls");
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
					String[][] data = {{meta.getString("BillToName"), meta.getString("InvoiceDate"),
						meta.getString("DateFrom"), meta.getString("DateTo")}};
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
					String[][] data = {{detail.getString("Carrier"),
						detail.getString("Group Number"), detail.getString("Claim Count"),
						detail.getString("Claim Amount"), detail.getString("Transaction Fee"),
						detail.getString("Total")}};
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
			BufferedImage image = ImageIO.read(new File("Images/logo.png"));
			PDImageXObject pdImageXObject = LosslessFactory.createFromImage(doc, image);

			PDPageContentStream contentStream = new PDPageContentStream(doc, page);

			// Build Header
			createHeader(contentStream, pdImageXObject, image, header);

			int pageLimit = 30; //30 rows per page
			int numberOfElementsInArray = content.length;
			int index = 1; //skip header row
			int elementsLeft = numberOfElementsInArray - 1; //don't count header row
			int finalChunkLength = 0;
			// Build Detail table
			if (numberOfElementsInArray > pageLimit) //if more than 30 detail rows
			{
				while (index < numberOfElementsInArray) //handle pagination
				{

					String[][] chunk = new String[pageLimit][numberOfElementsInArray];
					System.arraycopy(content, 0, chunk, 0, 1); //copy header row
					System.arraycopy(content, index, chunk, 1, (elementsLeft > 29) ? 29 : elementsLeft); //copy 29 other rows
					drawTable(page, contentStream, detailTableY, 30f, chunk);
					finalChunkLength = chunk.length;
					elementsLeft -= 29;
					index += 29;
					if (elementsLeft > 0)
					{
						contentStream.close();
						page = new PDPage();
						doc.addPage(page);
						contentStream = new PDPageContentStream(doc, page);
						createHeader(contentStream, pdImageXObject, image, header);
					}
				}
			}
			else //single page report
			{
				drawTable(page, contentStream, detailTableY, 30f, content);
			}

			// If details use half the page, then print totals on new page
			if (finalChunkLength > 15 || content.length > 15)
			{
				contentStream.close();
				page = new PDPage();
				doc.addPage(page);
				contentStream = new PDPageContentStream(doc, page);
				createHeader(contentStream, pdImageXObject, image, header);
				// Totals
				createTotals(contentStream, detailTableY);
			}
			else
			{
				// Totals
				createTotals(contentStream, detailTableY - (40f * content.length));
			}

			contentStream.close();

			// Persist document
			doc.save("OriginalFiles/claim_summary.pdf");

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
		String[][] content = {{"", "", "Prescription Claims", "Transaction Fees"}, {"Grand Total", grandTotalCount.toString(), grandTotalAmount.toString(), grandTotalFee.toString()}};
		drawTotalTable(contentStream, y, 120f, content);
	}

	private static void createHeader(PDPageContentStream contentStream, PDImageXObject pdImageXObject,
		BufferedImage image, String[][] header) throws IOException, ParseException
	{
		PDFont font = PDType1Font.HELVETICA;

		// RxBenefits Image
		contentStream.drawImage(pdImageXObject, 20, 740, image.getWidth() / 3, image.getHeight() / 3);

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
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset(50, 704);
		contentStream.showText("1-800-334-8134");
		contentStream.endText();

		// Invoice date and period table
		drawHorizontalHeader(contentStream, 747, 463, header);

		// Administrator line
		font = PDType1Font.TIMES_ROMAN;
		contentStream.beginText();
		contentStream.setFont(font, 10);
		contentStream.newLineAtOffset(30, 684);
		contentStream.showText("Administrator of:   Pharmacy Programs");
		contentStream.endText();
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
					if (i > 0 && j == 2) //TransactionCount
					{
						contentStream.showText(text);
						grandTotalCount = grandTotalCount.add(new BigDecimal(text));
					}
					else if (i > 0 && j == 3) //TransactionAmount
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
						grandTotalAmount = grandTotalAmount.add(new BigDecimal(text));
					}
					else if (i > 0 && j == 4) //TransactionFee
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
						grandTotalFee = grandTotalFee.add(new BigDecimal(text));
					}
					else if (i > 0 && j == 5) //Total
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
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

	private static void drawHorizontalHeader(PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException, ParseException
	{
		final float rowHeight = 10f;
		final float tableWidth = 120f;
		final float tableHeight = 7*10f;
		final float cellMargin = 4f;
		final float offset = 56f;

		// draw the box
		contentStream.addRect(margin, y+cellMargin+rowHeight/2, tableWidth, -tableHeight-(rowHeight/2));
		contentStream.stroke();

		contentStream.setFont(PDType1Font.HELVETICA, 8);

		// Bill To
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin, y);
		contentStream.showText("For:");
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + ("For:".length()*cellMargin) + (cellMargin*3), y);
		contentStream.showText(content[1][0]);
		contentStream.endText();

		// Invoice Date
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin, y - rowHeight*2);
		contentStream.showText("Invoice Date:");
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + ("Invoice Date:".length()*cellMargin) + (cellMargin*3), y - rowHeight*2);
		contentStream.showText(new SimpleDateFormat("MM/dd/yyyy").format(new SimpleDateFormat("yyyy-MM-dd").parse(content[1][1])));
		contentStream.endText();

		// Centered Period Covered
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin + offset - "Period Covered".length() * 2, y-rowHeight*4);
		contentStream.showText("Period Covered");
		contentStream.endText();

		// Date From and Date To
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin, y - (rowHeight*6));
		if (content[1][2] == null)
		{
			contentStream.showText("Null Date");
		}
		contentStream.showText(new SimpleDateFormat("MM/dd/yyyy").format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(content[1][2])));
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + offset, y - (rowHeight*6));
		contentStream.showText("to");
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin + offset + (cellMargin*3), y - (rowHeight*6));
		if (content[1][3] == null)
		{
			contentStream.showText("Null Date");
		}
		contentStream.showText(new SimpleDateFormat("MM/dd/yyyy").format(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(content[1][3])));
		contentStream.endText();
	}

	private static void drawTotalTable(PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException
	{
		final int rows = content.length;
		final int cols = content[0].length;
		final float rowHeight = 20f;
		final float tableWidth = 370f;
		final float tableHeight = rowHeight * rows;
		final float colWidth = tableWidth / (float) cols;
		final float cellMargin = 2f;

		// draw the rows
		float nexty = y;
		for (int i = 0; i <= rows; i++)
		{
			contentStream.moveTo(margin, nexty);
			contentStream.lineTo(margin + tableWidth, nexty);
			contentStream.stroke();
			nexty -= rowHeight;
		}

		// draw the first and last columns
		float nextx = margin;
		for (int i = 0; i <= cols; i++)
		{
			if (i == 0 || i == cols)
			{
				contentStream.moveTo(nextx, y);
				contentStream.lineTo(nextx, y - tableHeight);
				contentStream.stroke();
			}
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

					contentStream.beginText();
					contentStream.newLineAtOffset(textx, texty);

				if (text != null)
				{
					if (i > 0 && j > 1)
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
					}
					else
					{
						contentStream.showText(text);
					}
				}
				contentStream.endText();
				textx += colWidth;
			}

			texty -= rowHeight;
			textx = margin + cellMargin;
		}
	}
}
