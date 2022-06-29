import java.io.*;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
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
    String[] Captions = new String[]{"ID", "RawAddress", "RawPunkt_old", "RawStreet_old",
            "RawHouseNumber_old", "PeopleCount", "_id",
            "Municipalitet", "Street", "HouseNumber", "AddressNotDecomposed", "oarID"};


    int size = Captions.length;

    public MongoCursor<Document> getData() throws IOException, ParseException {

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

        MongoClient mongo = MongoClients.create(); //this.connectionString
        MongoDatabase db = mongo.getDatabase(this.database);
        MongoCollection<Document> collection = db.getCollection(this.dataset);
        MongoCollection<Document> structure = db.getCollection("datasetsStructure");

        Document struc;

        //* IMPORT *//
        reader = new FileReader("datasetsStructure.json");


        JSONArray structureJSON = (JSONArray) jsonParser.parse(reader);
        for (Object object : structureJSON) {
            struc = Document.parse(object.toString());
            //structure.insertOne(struc);
        }
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
            sort.put(sortBy, order);
            cursor = collection.find((Bson) filters).sort(sort).limit(10).iterator();
            //cursor = collection.find((Bson) filters).sort(sort).iterator();

        } else {
            cursor = collection.find((Bson) filters).iterator();
        }

        if (this.format.equals("XLSX") || this.format.equals("XSLX")) {
            writeXLSX(cursor);
        } else if (this.format.equals("JSON")) {
            writeJson(cursor);
        }
        return cursor;
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
        Document d;

        File file = new File("temp.xlsx");
        FileOutputStream output = new FileOutputStream(file);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(this.database);

        Row header = sheet.createRow(0);
        for (int j = 0; j < size; j++) {
            Cell headerCell = header.createCell(j);
            headerCell.setCellValue(this.Captions[j]);
        }

        for (int k = 1; k < 9 && cursor.hasNext(); k++) {
            Row row = sheet.createRow(k);
            d = cursor.next();
            //System.out.println(d);
            for (int j = 0; j < 11; j++) {
                Cell headerCell = row.createCell(j);
                headerCell.setCellValue(d.get(this.Captions[j]).toString());
            }
        }

        workbook.write(output);
        workbook.close();
    }
}
