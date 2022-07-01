import java.io.*;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.*;
import com.mongodb.util.JSON;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Exporter {
    String ConnectionString = "";
    String database = "";
    String dataset = "";

    String sortBy = "";
    String sortOrder = "";
    String format = "";
    String resultFile = "";

    JSONArray list;

    public void getData() throws IOException, ParseException {

        JSONParser jsonParser = new JSONParser();
        FileReader reader;
        reader = new FileReader("settings.json");
        JSONObject obj = (JSONObject) jsonParser.parse(reader);
        JSONObject params = (JSONObject) obj.get("params");
        JSONObject filtersJSON = (JSONObject) params.get("filter");

        this.ConnectionString = (String) params.get("ConnectionString");
        this.database = (String) params.get("database");
        this.dataset = (String) params.get("dataset");

        this.sortBy = (String) params.get("sortBy");
        this.sortOrder = (String) params.get("sortOrder");
        this.format = (String) params.get("format");
        this.resultFile = (String) params.get("resultFile");

        System.out.println("Сортировка: " + this.sortBy + ' ' +  this.sortOrder);

        MongoClient mongo = MongoClients.create(this.ConnectionString); //this.connectionString
        MongoDatabase db = mongo.getDatabase(this.database);
        MongoDatabase db2 = mongo.getDatabase("rk_metadata");
        System.out.println(this.database);
        MongoCollection<Document> collection = db.getCollection(this.dataset);
        Document doc = collection.find().first();
        System.out.println("1: " + doc);
        MongoCollection<Document> structure = db2.getCollection("datasetsStructure");
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
           cursor = collection.find(filter).sort(sort).limit(100).iterator();
            //cursor = collection.find(filter).sort(sort).iterator();


        } else {
            cursor = collection.find(filter).limit(100).iterator();
         // cursor = collection.find().iterator();
        }
        /*while (cursor.hasNext()) {
            System.out.println(cursor.next());
        }*/

        if (this.format.equals("XLSX") || this.format.equals("XSLX")) {
           writeXLSX(cursor, this.resultFile);
        } else if (this.format.equals("JSON")) {
            writeJson(cursor, this.resultFile);
        }
        mongo.close();
    }

    public void writeJson(MongoCursor<Document> cursor, String path) throws IOException {
        File file = new File("test.json");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        while (cursor.hasNext()) {
            writer.write(cursor.next().toJson());
        }
    }

    public void writeXLSX(MongoCursor<Document> cursor, String path) throws IOException {

        int cellCount = list.size();
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
              // System.out.println(type);

                if (type.equals("oarObject")) {
                    System.out.println("dsgfs");
                   // System.out.println(d.get("name"));
                    Document doc = (Document) d.get("oarObject");
                    System.out.println(doc.values());

                    System.out.println(doc.keySet()); // может пригодиться
                    //JSONObject oar = (JSONObject) d.get("oarObject"); // получение оар объектов
                    //System.out.println(oar);
                    //JSONObject js = (JSONObject) jsonParser.parse(ob);
                    //JSONArray oar = (JSONArray) d.get("oarObject");
                   // d.get("name").getClass();

                    //System.out.println(oar);
                    //тут должна быть обработка оар объекта
                }
                else {
                    String out = d.get(name) == null ? "" : d.get(name).toString();
                    headerCell.setCellValue(out);
                }

            }
        }
        workbook.write(output);
        workbook.close();
    }
}
