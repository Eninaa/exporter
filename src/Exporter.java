import java.io.*;

import static com.mongodb.client.model.Projections.*;
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
    long docCount;

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

        System.out.println(this.sortBy + ' ' +  this.sortOrder);

        MongoClient mongo = MongoClients.create(); //this.connectionString
        MongoDatabase db = mongo.getDatabase(this.database);
        MongoCollection<Document> collection = db.getCollection(this.dataset);
        this.docCount = collection.countDocuments();
        MongoCollection<Document> structure = db.getCollection("datasetsStructure");

        String con = String.format("{dataset: '%s', database: '%s' }", this.dataset, this.database);
        DBObject fil = (DBObject) JSON.parse(con);
        //String string = String.format("A String %s %s", database, dataset);
        // "{dataset: 'ud_1_628e2166f844c10cc93c39f3'}" такое мы понимаем
        System.out.println(con);
        //{},  {your_key:1, _id:0}
        Bson b = (Bson) fil;
        System.out.println(b.toString());
        MongoCursor<Document> it = structure.find().iterator();

        this.list = new JSONArray();

      while(it.hasNext()){
          String ob = it.next().toJson();
          //JSONParser parser = new JSONParser();
          JSONObject js = (JSONObject) jsonParser.parse(ob);
          this.list = (JSONArray) js.get("fields");
        }

      System.out.println(list.toString());
        //* IMPORT *//
    /*    reader = new FileReader("datasetsStructure.json");


        JSONArray structureJSON = (JSONArray) jsonParser.parse(reader);
        for (Object object : structureJSON) {
            struc = Document.parse(object.toString());
            //structure.insertOne(struc);
        }
*/
        //String json = JSON.serialize(fil);
        DBObject filters = (DBObject) JSON.parse(filtersJSON.toString());
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
            //cursor = collection.find((Bson) filters).sort(sort).limit(10).iterator();
            cursor = collection.find((Bson) filters).sort(sort).iterator();

        } else {
            //cursor = collection.find((Bson) filters).limit(10).iterator();
            cursor = collection.find((Bson) filters).iterator();
        }

        if (this.format.equals("XLSX") || this.format.equals("XSLX")) {
            writeXLSX(cursor);
        } else if (this.format.equals("JSON")) {
            writeJson(cursor);
        }
        mongo.close();
    }


    public void writeJson(MongoCursor<Document> cursor) throws IOException {
        File file = new File("Exp.json");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        while (cursor.hasNext()) {
            writer.write(cursor.next().toJson());
        }
    }

    public void writeXLSX(MongoCursor<Document> cursor) throws IOException {

        int cellCount = list.size();
        Document d;

        File file = new File("temp.xlsx");
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
        for (int k = 1; k < this.docCount && cursor.hasNext(); k++) {
            Row row = sheet.createRow(k);
            d = cursor.next();
            for (int j = 0; j < cellCount; j++) {
                Cell headerCell = row.createCell(j);
                JSONObject names = (JSONObject) list.get(j);
                String name = (String) names.get("name");
                if (name.equals("oarObject")) {
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
