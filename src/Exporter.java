import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.BsonDateTime;
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
    String commonDir = "";
    String path = "";
    ObjectId taskId;
    ObjectId datasetId;

    double operationsCount;
    double completedOperations;
    double progress;
    boolean completed = false;
    MongoCollection<Document> tasks;
    MongoDatabase metadata;

    JSONArray list;

    public void getData() throws IOException, ParseException {

        JSONParser jsonParser = new JSONParser();
        Reader reader = new InputStreamReader(new FileInputStream("settings.json"),StandardCharsets.UTF_8);
        JSONObject obj = (JSONObject) jsonParser.parse(reader);
        JSONObject params = (JSONObject) obj.get("params");
        JSONObject filtersJSON = (JSONObject) params.get("filter");
        
        this.taskId = new ObjectId((String) params.get("taskId"));
        this.ConnectionString = (String) params.get("ConnectionString");
        this.database = (String) params.get("database");
        this.dataset = (String) params.get("dataset");

        this.sortBy = (String) params.get("sortBy");
        this.sortOrder = (String) params.get("sortOrder");
        this.format = (String) params.get("format");
        this.resultFile = (String) params.get("resultFile");
        this.commonDir = (String) params.get("commonDir");
        //this.path = this.commonDir.concat(resultFile);
      //  this.datasetId = new ObjectId((String) params.get("datasetId"));

        MongoClient mongo = MongoClients.create(this.ConnectionString);
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

        MongoCursor<Document> cursor;
        if (this.sortOrder != null) {
            int order = 0;
            if (this.sortOrder.equalsIgnoreCase("desc")) {
                order = -1;
            } else if (this.sortOrder.equalsIgnoreCase("asc")) {
                order = 1;
            }
            sort.put(this.sortBy, order);
            cursor = collection.find(filter).iterator();
        } else {
           cursor = collection.find(filter).sort(sort).iterator();

        }

        if (this.format.equalsIgnoreCase("XLSX") || this.format.equalsIgnoreCase("XSLX")) {
            writeXLSX(cursor, this.resultFile);
        } else if (this.format.equalsIgnoreCase("JSON") || this.format.equalsIgnoreCase("GEOJSON")) {
            writeGEOJson(cursor, this.resultFile);
        }
        cursor.close();
        mongo.close();
    }
    public void writeGEOJson(MongoCursor<Document> cursor, String path) throws IOException, ParseException {

        File file = new File("test.geojson");
        file.createNewFile();
        Writer wr = new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8);

        Document doc;
        Document d = new Document();
        String out = "";
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
                String strucFeatures = (String) names.get("feature");

                if (name.contains(".")) {
                    String[] nameParts = name.split("\\p{Punct}");
                    for (int j = 0; j < nameParts.length - 1; j++) {
                        d = (Document) doc.get(nameParts[j]);
                        if(type.equals("oarObject") || type.equals("geometry")) {
                            if (d != null) {
                                d = (Document) d.get(nameParts[++j]);
                            }
                        } else if (type.equals("ObjectId")) {
                            ObjectId id = (ObjectId) d.get(nameParts[++j]);
                            if (!id.equals(null)) {
                                out = id.toString();
                            }
                        } else {
                            out = d.get(nameParts[++j]) == null ? "" : d.get(nameParts[++j]).toString();
                        }
                    }
                } else {
                    if (type.equals("oarObject") || type.equals("geometry")) {
                        d = (Document) doc.get(name);
                    } else if(type.equals("ObjectId")) {
                        Object id = doc.get(name);
                        if (!id.equals(null)) {
                            out = id.toString();
                        }
                    } else {
                        out = doc.get(name) == null ? "" : doc.get(name).toString();
                    }
                }
                if (strucFeatures != null && strucFeatures.equals("Geometry")) {
                    if(d != null) {
                        geometry.put("type", d.get("type"));
                        geometry.put("coordinates", d.get("coordinates"));
                        feature.put("geometry", geometry);
                        geo = true;
                    }
                } else if (type.equals("oarObject")) {
                    if(d != null) {
                        properties.put(name, d);
                    }
                } else if (type.equals("ObjectId")) {
                    properties.put(name, d);
                } else {
                    properties.put(name, out);
                }
            }
            feature.put("properties", properties);
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

        wr.write(featureCollection.toJSONString());
        wr.flush();
        wr.close();

    }
    public void writeXLSX(MongoCursor<Document> cursor, String path) throws IOException, ParseException {
        int cellCount = list.size();
        Document doc;
        Document d;
        String out = "";
        File file = new File("test.xlsx");
        file.createNewFile();
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
            doc = cursor.next();
            for (int j = 0; j < cellCount; j++) {
                Cell headerCell = row.createCell(j);
                JSONObject names = (JSONObject) list.get(j);
                String name = (String) names.get("name");
                String type = (String) names.get("type");
                if(name.contains(".")) {
                    String[] nameParts = name.split("\\p{Punct}");
                    for (int i = 0; i < nameParts.length-1; i++) {
                        d = (Document) doc.get(nameParts[i]); // предполагаю что первый уровень многоуровневого поля может быть только объектом
                        if(type.equals("oarObject") || type.equals("geometry")) {
                            if (d != null) {
                                Document t = (Document) d.get(nameParts[++i]);
                                out = t.toJson();
                            }
                        } else if (type.equals("ObjectId")) {
                            ObjectId id = (ObjectId) d.get(nameParts[++i]);
                            if (!id.equals(null)) {
                                out = id.toString();
                            }
                        } else {
                            out = d.get(nameParts[++i]) == null ? "" : d.get(nameParts[++i]).toString();
                        }
                    }
                } else {
                    if (type.equals("oarObject") || type.equals("geometry")) {
                        d = (Document) doc.get(name);
                        if( d!= null) {
                            out = d.toJson();
                        }
                    } else if(type.equals("ObjectId")) {
                        Object id = doc.get(name);
                        if (!id.equals(null)) {
                            out = id.toString();
                        }
                    } else {
                        out = doc.get(name) == null ? "" : doc.get(name).toString();
                    }
                }
                    headerCell.setCellValue(out);
            }
            this.completedOperations ++;
            writeProgress();
        }
        workbook.write(output);
        workbook.close();
    }

    public void writeProgress() {

        this.progress = 1 * this.completedOperations / this.operationsCount;
        if (this.completedOperations % 1000 == 0) {
            writeInfo();
        }
       if (this.progress == 1 ) {
            this.completed = true;
            writeInfo();
            MongoCollection<Document> f;
            BasicDBObject task = new BasicDBObject();
            task.put("dataset", this.dataset);
            task.put("_id", this.datasetId);

            JSONObject info = new JSONObject();
            BsonDateTime dateTime = new BsonDateTime(Instant.now().toEpochMilli());

            info.put("status", true);
            info.put("time", dateTime);
            info.put("file", this.resultFile);
           Bson update = Updates.push("export", info);
             f = this.metadata.getCollection("userDatasets");
             if (!f.find(task).iterator().hasNext()) {
                 f = this.metadata.getCollection("regionalDatasets");
             }
             f.updateOne(task,update);
        }
    }
    public void writeInfo() {
        BasicDBObject task = new BasicDBObject();
        BasicDBObject updateObject = new BasicDBObject();
        task.put("_id", this.taskId);

        JSONObject lastInfo = new JSONObject();
        JSONObject info = new JSONObject();
        JSONArray errors = new JSONArray();
        JSONObject status = new JSONObject();

        if (this.completed) {
            status.put("status", "complete");
            updateObject.put("$set", status);
            tasks.updateOne(task, updateObject);
        }

        info.put("progress", this.progress);
        info.put("completed", this.completed);
        info.put("errors", errors);
        lastInfo.put("lastInfo", info);

        updateObject.put("$set", lastInfo);
        tasks.updateOne(task, updateObject);
    }
}
