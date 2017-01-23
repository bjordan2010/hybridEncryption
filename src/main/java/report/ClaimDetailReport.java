package report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by bjordan on 1/20/17.
 * This class is responsible for creating the claim detail in PDF or Excel format.
 */
public class ClaimDetailReport
{
	private static BigDecimal grandTotalAmount = new BigDecimal(0);
	private static BigDecimal subTotalAmount = new BigDecimal(0);
	private static String previousCarrier = null;
	private static String previousGroupNumber = null;
	private static Integer detailTableY = 522;

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
		String[][] header = {{"Name", "DateFrom", "DateTo", "InvoiceDate"}};
		String[][] content = {{"Carrier", "Group Number", "generatedId", "clientID", "ssn", "employeeIdSource",
			"Subscriber Last Name", "Subscriber First Name", "Dependent Last Name", "Dependent First Name",
			"Transaction ID", "Date Of Service", "Product Service ID", "Product Name", "Amount Billed"}};

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
				.append("select organization.Name as Name, min(transactionbillingperiod.DateFrom) as DateFrom, ")
				.append("max(transactionbillingperiod.DateTo) as DateTo, invoice.InvoiceDate ")
				.append("from invoice ")
				.append("left outer join transactionbillingperiod ON transactionbillingperiod.InvoicePeriodNo = invoice.InvoicePeriodNo ")
				.append("left outer join invoiceconfig ON invoiceconfig.invoiceConfigNo = invoice.invoiceConfigNo ")
				.append("left outer join organization ON organization.OrganizationNo = invoiceconfig.OrganizationNo ")
				.append("where invoice.InvoiceNo = ")
				.append(invoiceNo).toString();
			String sqlDetail = new StringBuffer()
				.append("select memberclaim.contractNumber as Carrier, memberclaim.groupNumber as `Group Number`, ")
				.append("employee.generatedId, employee.clientId, employee.socialSecurityNumber as ssn, plan.employeeIdSource, ")
				.append("employee.lastName as `Subscriber Last Name`, employee.firstName as `Subscriber First Name`, ")
				.append("dependent.lastName as `Dependent Last Name`, dependent.firstName as `Dependent First Name`, ")
				.append("memberclaim.transactionId as `Transaction ID`, memberclaim.dateOfService as `Date Of Service`, ")
				.append("memberclaim.productServiceId as `Product Service ID`, memberclaim.productName as `Product Name`, ")
				.append("memberclaim.totalAmountBilled as `Amount Billed` ")
				.append("from memberclaim ")
				.append("left outer join transactionbillingdetail on transactionbillingdetail.memberClaimNo = memberclaim.memberClaimNo ")
				.append("left outer join transactionbillingsummary on transactionbillingsummary.transactionBillingSummaryNo = transactionbillingdetail.transactionBillingSummaryNo ")
				.append("left outer join transactionbillingperiod on transactionbillingperiod.transactionBillingPeriodNo = transactionbillingdetail.transactionBillingPeriodNo ")
				.append("left outer join invoice on invoice.invoicePeriodNo = transactionbillingperiod.invoicePeriodNo ")
				.append("left outer join employee on employee.employeeNo = memberclaim.employeeNo ")
				.append("left outer join dependent on dependent.dependentNo = memberclaim.dependentNo ")
				.append("left outer join benefitplan on benefitplan.benefitPlanNo = transactionbillingsummary.benefitPlanNo ")
				.append("left outer join plan on plan.planNo = benefitplan.planNo ")
				.append("where invoice.invoiceNo = ")
				.append(invoiceNo)
				.append(" order by memberclaim.contractNumber and memberclaim.groupNumber").toString();

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

			HSSFSheet sheet = workbook.createSheet("claim_detail");

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
						if (i == 14)
						{
							arow.createCell((short) i, 0).setCellValue((detail.getDouble(content[0][i])));
						}
						else
						{
							arow.createCell((short) i).setCellValue((detail.getString(content[0][i])));
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
				HSSFRow arow = sheet.createRow((short)row);
				arow.createCell((short)13).setCellValue("Grand Total");
				String formula = "sum(O4:O"+(row)+")";
				arow.createCell((short)14).setCellFormula(formula);

				fileOut = new FileOutputStream("OriginalFiles/claim_detail.xls");
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
					String[][] data = {{meta.getString("Name"), meta.getString("DateFrom"),
						meta.getString("DateTo"), meta.getString("InvoiceDate")}};
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
					String[][] data = {{detail.getString("Carrier"), detail.getString("Group Number"),
						detail.getString("generatedId"), detail.getString("clientId"),
						detail.getString("ssn"), detail.getString("employeeIdSource"),
						detail.getString("Subscriber Last Name"), detail.getString("Subscriber First Name"),
						detail.getString("Dependent Last Name"), detail.getString("Dependent First Name"),
						detail.getString("Transaction ID"), detail.getString("Date Of Service"),
						detail.getString("Product Service ID"), detail.getString("Product Name"),
						detail.getString("Amount Billed")}};
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
			// Landscape
			PDPage page = new PDPage(new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth()));
			doc.addPage(page);

			PDPageContentStream contentStream = new PDPageContentStream(doc, page);

			// Build Header
			createHeader(contentStream, header);

			int pageLimit = 11; //11 rows per page due to subtotals
			int numberOfElementsInArray = content.length;
			int index = 1; //skip header row
			int elementsLeft = numberOfElementsInArray - 1; //don't count header row
			int finalChunkLength = 0;
			// Build Detail table
			if (numberOfElementsInArray > pageLimit) //if more than 11 detail rows
			{
				while (index < numberOfElementsInArray) //handle pagination
				{

					String[][] chunk = new String[pageLimit][numberOfElementsInArray];
					System.arraycopy(content, 0, chunk, 0, 1); //copy header row
					System.arraycopy(content, index, chunk, 1, (elementsLeft > 10) ? 10 : elementsLeft); //copy 29 other rows
					drawTable(page, contentStream, detailTableY, 30f, chunk);
					finalChunkLength = chunk.length;
					elementsLeft -= 10;
					index += 10;
					if (elementsLeft > 0)
					{
						contentStream.close();
						page = new PDPage();
						doc.addPage(page);
						contentStream = new PDPageContentStream(doc, page);
						createHeader(contentStream, header);
					}
				}
			}
			else //single page report
			{
				drawTable(page, contentStream, detailTableY, 30f, content);
			}

			// If details use half the page, then print subtotals on new page
			if (finalChunkLength > 9 || content.length > 9)
			{
				contentStream.close();
				page = new PDPage();
				doc.addPage(page);
				contentStream = new PDPageContentStream(doc, page);
				createHeader(contentStream, header);
				// Totals
				createSubTotals(contentStream, detailTableY);
			}
			else
			{
				//Sub Totals
				detailTableY -= (20*content.length + 10);
				createSubTotals(contentStream, detailTableY);
				detailTableY -= 30;
			}

			// Totals
			createTotals(contentStream, detailTableY);

			contentStream.close();

			// Persist document
			doc.save("OriginalFiles/claim_detail.pdf");

			// Close document
			doc.close();

			System.out.println("PDF Created.");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private static void createSubTotals(PDPageContentStream contentStream, float y) throws IOException
	{
		String[][] content = {{previousCarrier, previousGroupNumber, grandTotalAmount.toString()}};
		drawTotalTable(contentStream, y, 30f, content);
	}

	private static void createTotals(PDPageContentStream contentStream, float y) throws IOException
	{
		String[][] content = {{"Grand Total", grandTotalAmount.toString()}};
		drawTotalTable(contentStream, y, 30f, content);
	}

	private static void createHeader(PDPageContentStream contentStream, String[][] header) throws IOException, ParseException
	{
		PDFont font = PDType1Font.HELVETICA;

		// Client Name
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset(30, 582);
		contentStream.showText("Client Name:");
		contentStream.endText();
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset(30 + ("Client Name".length() * 6f) + 12f, 582);
		contentStream.showText(header[1][0]);
		contentStream.endText();

		// Title
		contentStream.beginText();
		contentStream.setFont(font, 11);
		contentStream.newLineAtOffset((792-60)/2 - ("Claim Details".length() * 3f) , 562);
		contentStream.showText("Claim Details");
		contentStream.endText();

		// Invoice Date and period table
		drawHorizontalHeader(contentStream, 582, 642, header);
	}

	private static void drawTable(PDPage page, PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException, ParseException
	{
		// Table Settings
		final int rows = content.length;
		final int cols = content[0].length - 5;  //Skip carrier, group number, and 3 of 4 identity fields
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

		float textx = margin + cellMargin;
		float texty = y - 15;
		float subtexty = texty;

		for (int i = 0; i < content.length; i++)
		{
			// Determine Member ID
			String generatedId = null;
			String clientId = null;
			String ssn = null;
			String employeeIdSource = null;

			String[] aContent = content[i];
			for (int j = 0; j < aContent.length; j++)
			{
				String text = aContent[j];

				if (text != null)
				{
					if (i == 0) //Header Row
					{
						contentStream.setFont(PDType1Font.HELVETICA_BOLD, 6);
						if (j == 2) //Member ID first
						{
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							contentStream.showText("Member ID");
							contentStream.endText();
						}
						else if (j < 2 || (j > 2 && j < 6)) //Skip carrier, group number, and identity headers
						{
							continue;
						}
						else //show all other headers
						{
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							contentStream.showText(text);
							contentStream.endText();
						}
					}
					if (i > 0) //Value rows
					{
						contentStream.setFont(PDType1Font.HELVETICA_BOLD, 8);
						if (j == 0)
						{
							if (!text.equalsIgnoreCase(previousCarrier))
							{
								//Print totals if this isn't the first line
								if (i > 1)
								{
									subtexty = texty - (rowHeight*1.5f);
									createSubTotals(contentStream, subtexty);
									subTotalAmount = new BigDecimal(0);
								}
								else //Print the carrier header
								{
									float subheadertexty = texty + rowHeight*2.5f;
									previousCarrier = text;
									contentStream.beginText();
									contentStream.newLineAtOffset(margin + cellMargin, subheadertexty);
									contentStream.showText("Carrier:");
									contentStream.endText();
									contentStream.beginText();
									contentStream.newLineAtOffset(margin + cellMargin + 40f, subheadertexty);
									contentStream.showText(previousCarrier);
									contentStream.endText();
								}
							}
							else
							{
								continue;
							}
						}
						else if (j == 1)
						{
							if (!text.equalsIgnoreCase(previousGroupNumber))
							{
								//Print totals if this isn't the first line && we carrier didn't break
								if (i > 1 && (subTotalAmount.compareTo(new BigDecimal(0)) != 0))
								{
									subtexty = texty - (rowHeight*1.5f);
									createSubTotals(contentStream, subtexty);
									subTotalAmount = new BigDecimal(0);
								}
								else //Print the carrier header
								{
									float subheadertexty = texty + rowHeight * 2.5f;
									previousGroupNumber = text;
									contentStream.beginText();
									contentStream.newLineAtOffset(margin + cellMargin + 110f, subheadertexty);
									contentStream.showText("Group Number:");
									contentStream.endText();
									contentStream.beginText();
									contentStream.newLineAtOffset(margin + cellMargin + 180f, subheadertexty);
									contentStream.showText(previousGroupNumber);
									contentStream.endText();
								}
							}
							else
							{
								continue;
							}
						}
						else if (j == 2)
						{
							generatedId = text;
							continue;
						}
						else if (j == 3)
						{
							clientId = text;
							continue;
						}
						else if (j == 4)
						{
							ssn = text;
							continue;
						}
						else if (j == 5)
						{
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							employeeIdSource = text;
							switch (Integer.parseInt(employeeIdSource.trim()))
							{
								case 0:
									if (ssn != null)
									{
										contentStream.showText(ssn);
									}
									break;
								case 1:
									if (generatedId != null)
									{
										contentStream.showText(generatedId);
									}
									break;
								case 2:
									if (clientId != null)
									{
										contentStream.showText(clientId);
									}
									break;
								case 3:
									if (clientId != null && generatedId != null)
									{
										contentStream.showText(clientId + generatedId);
									}
									break;
								default:
									contentStream.showText("Invalid");
									break;
							}
							contentStream.endText();
						}
						else if (j == 11)
						{
							SimpleDateFormat shortDate = new SimpleDateFormat("yyyy-MM-dd");
							SimpleDateFormat longDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							contentStream.showText(shortDate.format(longDate.parse(text)));
							contentStream.endText();
						}
						else if (j == 14)
						{
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
							contentStream.endText();
							subTotalAmount = subTotalAmount.add(new BigDecimal(text));
							grandTotalAmount = grandTotalAmount.add(new BigDecimal(text));
						}
						else
						{
							contentStream.beginText();
							contentStream.newLineAtOffset(textx, texty);
							contentStream.showText(text);
							contentStream.endText();
						}
					}
				}
				if ((i == 0 && j == 2) || (i > 0 && j == 5) || j > 5)
				{
					textx += colWidth;
				}
			}

			if (subtexty != texty)
			{
				texty -= subtexty + rowHeight;
			}
			else
			{
				texty -= rowHeight;
			}
			textx = margin + cellMargin;
		}
	}

	private static void drawHorizontalHeader(PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException, ParseException
	{
		final float rowHeight = 10f;
		final float tableWidth = 120f;
		final float tableHeight = 5 * 10f;
		final float cellMargin = 4f;
		final float offset = 56f;

		// draw the box
		contentStream.addRect(margin, y + cellMargin + rowHeight / 2, tableWidth, -tableHeight - (rowHeight / 2));
		contentStream.stroke();

		contentStream.setFont(PDType1Font.HELVETICA, 8);

		// Bill To
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin, y);
		contentStream.showText("Invoice Date:");
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + ("Invoice Date:".length() * cellMargin) + (cellMargin * 3), y);
		contentStream.showText(content[1][3]);
		contentStream.endText();

		// Centered Period Covered
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin + offset - "Period Covered".length() * 2, y - rowHeight * 2);
		contentStream.showText("Period Covered");
		contentStream.endText();

		// Date From and Date To
		SimpleDateFormat shortDate = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat longDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin, y - (rowHeight * 4));
		if (content[1][1] == null)
		{
			System.err.println("Transaction Billing Period Date From cannot be null");
			content[1][1] = "2017-01-01 00:00:00";
		}
		contentStream.showText(shortDate.format(longDate.parse(content[1][1])));
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + offset, y - (rowHeight * 4));
		contentStream.showText("to");
		contentStream.endText();
		contentStream.beginText();
		contentStream.newLineAtOffset(margin + cellMargin + offset + (cellMargin * 3), y - (rowHeight * 4));
		if (content[1][2] == null)
		{
			System.err.println("Transaction Billing Period Date To cannot be null");
			content[1][2] = "2017-01-15 00:00:00";
		}
		contentStream.showText(shortDate.format(longDate.parse(content[1][2])));
		contentStream.endText();
	}

	private static void drawTotalTable(PDPageContentStream contentStream, float y, float margin,
		String[][] content) throws IOException
	{
		final int rows = content.length;
		final int cols = content[0].length;
		final float rowHeight = 20f;
		final float tableWidth = 792f - (2 * margin);
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

		float textx;
		if (cols < 3) //grand total
		{
			textx = margin + cellMargin;
		}
		else
		{
			textx = margin + cellMargin + 40f;
		}
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
					if (j == 1 && cols < 3) //grand total
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
					}
					else if (j == 2 && cols > 2)
					{
						contentStream.showText(NumberFormat.getCurrencyInstance().format(Double.parseDouble(text)));
					}
					else
					{
						contentStream.showText(text);
					}
				}
				contentStream.endText();
				if (cols < 3) //grand total
				{
					textx = tableWidth - 42f; //last column of grand totals
				}
				else
				{
					textx += 143f;
					if (j == 1)
					{
						textx = tableWidth - 42f; //last column of subtotals
					}
				}
			}

			texty -= rowHeight;
			textx = margin + cellMargin;
		}
	}
}
