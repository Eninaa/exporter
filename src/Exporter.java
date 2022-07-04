import java.io.*;

import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import com.mongodb.util.JSON;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.bson.conversions.Bson;
//import org.geotools.geojson.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.geotools.geojson.GeoJSON;


public class Exporter {

    String ConnectionString = "";
    String database = "";
    String dataset = "";

    String sortBy = "";
    String sortOrder = "";
    String format = "";
    String resultFile = "";

    JSONArray list; // список полей // captionList

    public void getData() throws IOException, ParseException {

        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("settings.json"); //
        JSONObject obj = (JSONObject) jsonParser.parse(reader);
        JSONObject params = (JSONObject) obj.get("params");
        JSONObject filtersJSON = (JSONObject) params.get("filter");

        this.ConnectionString = (String) params.get("ConnectionString");
        this.database = (String) params.get("database");
         this.dataset = (String) params.get("dataset");
        //this.database = "region63_samarskaya_obl";
       // this.dataset = "mar_houses";


        this.sortBy = (String) params.get("sortBy");
        this.sortOrder = (String) params.get("sortOrder");
        this.format = (String) params.get("format");
        this.resultFile = (String) params.get("resultFile");

        System.out.println("Сортировка: " + this.sortBy + ' ' + this.sortOrder);

        MongoClient mongo = MongoClients.create(this.ConnectionString); //this.connectionString
        MongoDatabase db = mongo.getDatabase(this.database);
        MongoDatabase metadata = mongo.getDatabase("rk_metadata");
        System.out.println(this.database);
        MongoCollection<Document> collection = db.getCollection(this.dataset);
        MongoCollection<Document> structure = metadata.getCollection("datasetsStructure");
        String str = String.format("{dataset: '%s', database: '%s' }", this.dataset, this.database);
        Bson structureFilter = (Bson) JSON.parse(str);
        System.out.println("Bson structureFilter: " + structureFilter);

        MongoCursor<Document> it = structure.find(structureFilter).iterator();

        this.list = new JSONArray();
        String ob = it.next().toJson();
        JSONObject js = (JSONObject) jsonParser.parse(ob);
        System.out.println(js);
        this.list = (JSONArray) js.get("fields");
        System.out.println("LIST: " + list.toString());


        Bson filter = (Bson) JSON.parse(filtersJSON.toString());
        BasicDBObject sort = new BasicDBObject();
        MongoCursor<Document> cursor;
        if (this.sortOrder != null) {
            int order = 0;
            if (this.sortOrder.equalsIgnoreCase("desc")) {
                order = -1;
            } else if (this.sortOrder.equalsIgnoreCase("asc")) {
                order = 1;
            }
            sort.put(this.sortBy, order);
            cursor = collection.find().limit(2).iterator();
            // cursor = collection.find(filter).sort(sort).iterator();
        } else {
            cursor = collection.find().limit(2).iterator();
            //cursor = collection.find(filter).iterator();
        }

        if (this.format.equals("XLSX") || this.format.equals("XSLX")) {
            writeXLSX(cursor, this.resultFile);
           // writeJson(cursor, this.resultFile);

        } else if (this.format.equals("JSON")) {
            writeJson(cursor, this.resultFile);
        }
        mongo.close();
    }

    public void writeJson(MongoCursor<Document> cursor, String path) throws IOException {
        File file = new File("test.json");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        Document doc;
        int cellCount = list.size();
        //GeometryJSON a = new GeometryJSON(1);
        GeoJSON a = new GeoJSON();


        while ((cursor.hasNext())) {
            doc = cursor.next();
            doc.keySet();
            for (int j = 0; j < cellCount; j++) {
                JSONObject captions = (JSONObject) list.get(j);
                String type = (String) captions.get("type");
                switch (type) {
                    case ("oarObject"):
                        break;
                    case("geometry"):
                        break;
                    default:
                        break;
                        }
            }
            writer.write("\n");
        }
    }

    public void writeXLSX(MongoCursor<Document> cursor, String path) throws IOException {

        int cellCount = list.size();
        System.out.println(list.toString());
        Document d;
        File file = new File("test.xlsx");
        FileOutputStream output = new FileOutputStream(file);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(this.database);
        Row header = sheet.createRow(0);
        for (int j = 0; j < cellCount; j++) {
            Cell headerCell = header.createCell(j);
            JSONObject captions = (JSONObject) list.get(j);
            String caption = (String) captions.get("caption");
            headerCell.setCellValue(caption);
        }
        for (int k = 1; cursor.hasNext(); k++) {
            Row row = sheet.createRow(k);
            d = cursor.next();
            for (int j = 0; j < cellCount; j++) {
                Cell headerCell = row.createCell(j);
                JSONObject names = (JSONObject) list.get(j);
                String name = (String) names.get("name");
                String type = (String) names.get("type");
                sheet.autoSizeColumn(j);
                Document doc;
                switch (type) {
                    case ("oarObject") -> {
                        doc = (Document) d.get("oarObject");
                        if (doc != null) {
                            String a = doc.toJson();
                            headerCell.setCellValue(a + "\n");
                        }
                    }
                    case ("geometry") -> {
                        doc = (Document) d.get("geometry");
                        if (doc != null) {
                            headerCell.setCellValue(doc.toJson());
                        }
                    }
                    /*case("string"):
                        String out = d.get(name) == null ? "" : d.get(name).toString();
                        headerCell.setCellValue(out);
                        break;*/
                    default -> {
                        String out = d.get(name) == null ? "" : d.get(name).toString();
                        headerCell.setCellValue(out);
                    }
                }
            }
        }
        workbook.write(output);
        workbook.close();
    }
}
