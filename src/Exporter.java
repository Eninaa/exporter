import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.conversions.Bson;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
    ObjectId taskId;
    double operationsCount;
    double completedOperations;
    double progress;
    boolean completed = false;
    MongoCollection<Document> tasks;
    MongoDatabase metadata;

    JSONArray list;

    public void getData() throws IOException, ParseException {

        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("settings.json"); //
        JSONObject obj = (JSONObject) jsonParser.parse(reader);
        JSONObject params = (JSONObject) obj.get("params");
        JSONObject filtersJSON = (JSONObject) params.get("filter");


        this.taskId = new ObjectId((String) params.get("taskId"));
        System.out.println(taskId);
        this.ConnectionString = (String) params.get("ConnectionString");
        //this.database = (String) params.get("database");
       // this.dataset = (String) params.get("dataset");
       //this.database = "region63_samarskaya_obl";
       // this.dataset = "mar_houses";
       this.database = "rk_userDatasets";
      this.dataset = "ud_1_625d2e90b5e13b0c5b442035";

        this.sortBy = (String) params.get("sortBy");
        this.sortOrder = (String) params.get("sortOrder");
        this.format = (String) params.get("format");
        this.resultFile = (String) params.get("resultFile");

        MongoClient mongo = MongoClients.create(this.ConnectionString); //this.connectionString
        MongoDatabase db = mongo.getDatabase(this.database);

        this.metadata = mongo.getDatabase("rk_metadata");
        this.tasks = metadata.getCollection("tasks");


        MongoCollection<Document> collection = db.getCollection(this.dataset);
        MongoCollection<Document> structure = metadata.getCollection("datasetsStructure");
        Bson structureFilter = Filters.and(Filters.regex("dataset", this.dataset), Filters.regex("database", this.database));
        MongoCursor<Document> it = structure.find(structureFilter).iterator();

        this.list = new JSONArray();
        String ob = it.next().toJson();
        JSONObject js = (JSONObject) jsonParser.parse(ob);
        this.list = (JSONArray) js.get("fields");

        Document filter = Document.parse(filtersJSON.toString());
        BasicDBObject sort = new BasicDBObject();
        this.operationsCount = collection.countDocuments(filter);
        System.out.println(collection.find().first());

        MongoCursor<Document> cursor;
        if (this.sortOrder != null) {
            int order = 0;
            if (this.sortOrder.equalsIgnoreCase("desc")) {
                order = -1;
            } else if (this.sortOrder.equalsIgnoreCase("asc")) {
                order = 1;
            }
            sort.put(this.sortBy, order);
            System.out.println(sort.toJson());
            cursor = collection.find().limit(50).iterator();
        } else {
            cursor = collection.find(filter).sort(sort).limit(50).iterator();
        }
System.out.println(cursor.available());
        if (this.format.equalsIgnoreCase("XLSX") || this.format.equalsIgnoreCase("XSLX")) {
            writeXLSX(cursor, this.resultFile);
            System.out.println("xlsx");
        } else if (this.format.equalsIgnoreCase("JSON") || this.format.equalsIgnoreCase("GEOJSON")) {
            writeGEOJson(cursor, this.resultFile);
            System.out.println("json");
        }
        cursor.close();
        mongo.close();
    }
    public void writeGEOJson(MongoCursor<Document> cursor, String path) throws IOException, ParseException {
        File file = new File("test.geojson"); //удалить
       // File file = new File(resultFile);
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        Document doc, d;
        int cellCount = list.size();
        boolean geo = false;

        JSONObject featureCollection = new JSONObject();
        featureCollection.put("type", "FeatureCollection");

        JSONArray features = new JSONArray();
        JSONObject feature = new JSONObject();
        JSONObject properties = new JSONObject();
        JSONObject geometry = new JSONObject();

        while (cursor.hasNext()) {
            doc = cursor.next();
            for (int i = 0; i < cellCount; i++) {
                JSONObject names = (JSONObject) list.get(i);
                String name = (String) names.get("name");
                String type = (String) names.get("type");
                System.out.println(type);
                String strucFeatures = (String) names.get("feature");

                if(name.contains(".")) {
                    Document t = null;
                    String[] nameParts = name.split("\\p{Punct}");
                    for (int j = 0; j < nameParts.length - 1; j++) {
                        d = (Document) doc.get(nameParts[j]);
                        if (d != null) {
                            t = (Document) d.get(nameParts[++j]);
                            if(strucFeatures!= null && strucFeatures.equals("Geometry")) {
                                geometry.put("type", t.get("type"));
                                geometry.put("coordinates", t.get("coordinates"));
                                feature.put("geometry", geometry);
                                geo = true;
                            } else {
                                properties.put(name, t);
                                feature.put("properties", properties);
                            }
                        }

                    }
                } else if (strucFeatures!= null && strucFeatures.equals("Geometry")) {
                    d = (Document) doc.get(name);
                    if (d != null) {
                        geometry.put("type", d.get("type"));
                        geometry.put("coordinates", d.get("coordinates"));
                        feature.put("geometry", geometry);
                    }
                    geo = true;
                } else if (name.equals("oarObject")) {
                    d = (Document) doc.get(name);
                    if (d != null) {
                        properties.put(name, d);
                        feature.put("properties", properties);
                    }
                } else {
                    String out = doc.get(name) == null ? "" : doc.get(name).toString();
                    if (out != null) {
                        properties.put(name, out);
                        feature.put("properties", properties);
                    }
                }
            }
            feature.put("type", "Feature");

            if (geo) {
                features.add(feature);
            }

            feature = new JSONObject();
            geometry = new JSONObject();
            properties = new JSONObject();
            this.completedOperations ++;
            writeProgress();
        }
        featureCollection.put("features", features);
        writer.write(featureCollection.toJSONString());
        writer.flush();
        writer.close();

    }
    public void writeXLSX(MongoCursor<Document> cursor, String path) throws IOException, ParseException {

        int cellCount = list.size();
        Document d;
        Document doc;
        Document temp = null;
        File file = new File("test.xlsx");
        file.createNewFile();
        //File file = new File(this.resultFile);
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
                if(name.contains(".")) {
                    Document t = null;
                    String[] nameParts = name.split("\\p{Punct}");
                    for (int i = 0; i < nameParts.length-1; i++) {
                        doc = (Document) d.get(nameParts[i]);
                        // t = (Document) doc.get(nameParts[++i]);
                        String out = d.get(nameParts[i]) == null ? "" : d.get(nameParts[++i]).toString();
                        headerCell.setCellValue(out);
                    }
                } else {
                    String out = null;
                    switch (type) {
                        case("oarObject") :
                        case("geometry") :
                            doc = (Document) d.get(name);
                            if (doc != null) {
                                out = doc.toJson();
                            }
                            break;
                        case("ObjectId") :
                            ObjectId id = (ObjectId) d.get(name);
                            if (!id.equals(null)) {
                                out = id.toString();
                            }
                            break;
                        /*case("int"):
                            Integer a = d.getInteger(name);
                            if(a != null) {
                                out = a.toString();
                            }
                            break;*/
                        default:
                            out = d.get(name) == null ? "" : d.get(name).toString();
                            break;
                    }
                    headerCell.setCellValue(out);

                }

            }
            this.completedOperations ++;
            writeProgress();
        }
        workbook.write(output);
        workbook.close();
    }

    public void writeProgress() {

        this.progress = 100 * this.completedOperations / this.operationsCount;

        if (this.completedOperations % 1000 == 0) {
            writeInfo();
        }
        if (this.progress == 100) {

            this.completed = true;
            writeInfo();

            MongoCollection<Document> f;
            BasicDBObject task = new BasicDBObject();
            BasicDBObject updateObject = new BasicDBObject();
            task.put("dataset", this.dataset);

            JSONObject export = new JSONObject();
            JSONArray exportInfo = new JSONArray();
            JSONObject info = new JSONObject();

            info.put("status", true);
            Date dateNow = new Date();
            SimpleDateFormat formatForDateNow = new SimpleDateFormat("yyyy.MM.dd 'T' hh:mm:ss z");
            info.put("time", formatForDateNow.format(dateNow));
            info.put("file", this.resultFile);
            exportInfo.add(info);
            export.put("export", exportInfo);

             f = this.metadata.getCollection("userDatasets");
             System.out.println(f.find(task).iterator().hasNext());
             if (!f.find(task).iterator().hasNext()) {
                 f = this.metadata.getCollection("regionalDatasets");
             }
             updateObject.put("$set", export);
             f.updateOne(task, updateObject);
        }
    }
    public void writeInfo() {

        BasicDBObject task = new BasicDBObject();
        BasicDBObject updateObject = new BasicDBObject();
        task.put("_id", this.taskId);

        BasicDBObject lastInfo = new BasicDBObject();
        BasicDBObject info = new BasicDBObject();

        info.put("progress", this.progress);
        info.put("completed", this.completed);
        info.put("errors", 0);
        lastInfo.put("lastInfo", info);

        updateObject.put("$set", lastInfo);
        tasks.updateOne(task, updateObject);
    }
}
