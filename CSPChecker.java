

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CSPChecker {

    static WebDriver driver;
    static Workbook workbook = new XSSFWorkbook();
    static Sheet sheet = workbook.createSheet("CSP_Errors");
    static int rowNum = 0;

    public static void main(String[] args) throws Exception {
        // Setup ChromeDriver path
        System.setProperty("Webdriver.chrome.driver", System.getProperty("user.dir") + "chromedriver");
        // Configure headless Chrome with logging
        ChromeOptions options = new ChromeOptions();
         options.addArguments("--headless=new"); // Uncomment to run headless
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        org.openqa.selenium.logging.LoggingPreferences logPrefs = new org.openqa.selenium.logging.LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, java.util.logging.Level.ALL);
        options.setCapability("goog:loggingPrefs", logPrefs);

        // Start WebDriver
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        // Create Excel Header
        createHeader();

        // List of sitemap URLs to crawl
        List<String> sitemapUrls = Arrays.asList(
             //Add the sitemap url here 
            
        );


        for (String sitemapUrl : sitemapUrls) {
            List<String> pageUrls = readUrlsFromSitemap(sitemapUrl);
            for (String pageUrl : pageUrls) {
                checkCSPForURL(pageUrl);
            }
        }

        // Cleanup
        driver.quit();

        // Save Excel file
        FileOutputStream out = new FileOutputStream("CSP_Errors.xlsx");
        workbook.write(out);
        out.close();

        System.out.println("✅ CSP error report generated successfully: CSP_Errors.xlsx");
    }

    // Create Excel headers
    public static void createHeader() {
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("URL");
        headerRow.createCell(1).setCellValue("CSP Error Message");
        headerRow.createCell(2).setCellValue("Timestamp");
    }

    // Parse a sitemap XML and extract <loc> URLs
    public static List<String> readUrlsFromSitemap(String sitemapUrl) {
        List<String> urls = new ArrayList<>();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(sitemapUrl).openConnection();
            connection.setRequestMethod("GET");

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(connection.getInputStream());

            NodeList nList = doc.getElementsByTagName("loc");
            for (int i = 0; i < nList.getLength(); i++) {
                urls.add(nList.item(i).getTextContent().trim());
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error reading sitemap: " + sitemapUrl);
        }
        return urls;
    }

    // Visit the page and extract CSP errors from browser console
    public static void checkCSPForURL(String pageUrl) {
        try {
            driver.get(pageUrl);
            Thread.sleep(2000); // wait for logs to load

            LogEntries logs = driver.manage().logs().get(LogType.BROWSER);
            boolean cspErrorFound = false;

            for (LogEntry entry : logs) {
                String message = entry.getMessage().toLowerCase();

                if (message.contains("content security policy") ||
                        message.contains("csp") ||
                        message.contains("refused to") ||
                        message.contains("violat") || // covers violate/violation
                        message.contains("blocked")) {

                    writeErrorToExcel(pageUrl, entry.getMessage());
                    cspErrorFound = true;
                }
            }

            if (!cspErrorFound) {
                System.out.println("✅ No CSP error: " + pageUrl);
            }
        } catch (Exception e) {
            System.out.println("❌ Error accessing: " + pageUrl + " - " + e.getMessage());
        }
    }

    // Write URL, error, and timestamp to Excel sheet
    public static void writeErrorToExcel(String url, String error) {
        Row row = sheet.createRow(rowNum++);
        row.createCell(0).setCellValue(url);
        row.createCell(1).setCellValue(error);
        row.createCell(2).setCellValue(new Date().toString());

        // Optional: highlight row in red
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i <= 2; i++) {
            row.getCell(i).setCellStyle(style);
        }

        System.out.println("❌ CSP Error found on: " + url);
    }
}
