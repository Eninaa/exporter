import java.io.*;
import java.util.Set;

import com.mongodb.BasicDBObject;
import com.mongodb.client.*;
import com.mongodb.util.JSON;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
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
       //this.database = "rk_userDatasets";
      //this.dataset = "ud_1_625d2e90b5e13b0c5b442035";


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
            cursor = collection.find().limit(3).iterator();
            // cursor = collection.find(filter).sort(sort).iterator();
        } else {
            cursor = collection.find().limit(3).iterator();
            //cursor = collection.find(filter).iterator();
        }

        if (this.format.equals("XLSX") || this.format.equals("XSLX")) {
          // writeXLSX(cursor, this.resultFile);
           writeJson(cursor, this.resultFile);

        } else if (this.format.equals("JSON")) {
            writeJson(cursor, this.resultFile);
        }
        mongo.close();
    }

    public void writeJson(MongoCursor<Document> cursor, String path) throws IOException, ParseException {
        File file = new File("test.geojson");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        Document doc, d;
        int cellCount = list.size();

        JSONObject featureCollection = new JSONObject();
        featureCollection.put("type", "FeatureCollection");

        JSONArray features = new JSONArray();
        JSONObject feature = new JSONObject();
        JSONObject properties = new JSONObject();
        JSONObject geometry = new JSONObject();
        feature.put("type", "Feature");
        features.add(feature);
        featureCollection.put("features", features);

        while(cursor.hasNext()) {
            doc = cursor.next();
            for (int j = 0; j < cellCount; j++) {
                JSONObject names = (JSONObject) list.get(j);
                System.out.println("names: " + names + j);
                String name = (String) names.get("name");
                String type = (String) names.get("type");

                if(type.equals("oarObject")) {
                    d = (Document) doc.get(name);
                    if(d != null) {
                        if(d.containsKey("Geometry")) {
                            Document temp = (Document) d.remove("Geometry");
                            System.out.println("temp: " + temp);
                            Object typee = temp.get("type");
                            Object coor = temp.get("coordinates");
                            geometry.put("type", typee);
                            geometry.put("coordinates", coor);
                            feature.put("geometry", geometry);
                            properties.put(name, d);
                            feature.put("properties", properties);
                        } else {
                            properties.put(name, d);
                            feature.put("properties", properties);

                        }
                    }

                } else if (type.equals("geometry")) {
                    if(name.contains(".")) {
                        Document t = null;
                        String[] nameParts = name.split("\\p{Punct}");
                        for (int i = 0; i < nameParts.length-1; i++) {
                            d = (Document) doc.get(nameParts[i]);
                            t = (Document) d.get(nameParts[++i]);
                            System.out.println("doc: " + t);
                        }
                        if (t != null) {
                            String featureStruc = (String) names.get("feature");
                            if (featureStruc != null) {
                                d = (Document) doc.get("Geometry");
                                Object typee = d.get("type");
                                Object coor = d.get("coordinates");
                                geometry.put("type", typee);
                                geometry.put("coordinates", coor);
                                feature.put("geometry", geometry);
                            }
                        }
                    }

                } else if (type.equals("ObjectId")) {
                    ObjectId id = (ObjectId) doc.get(name);
                    Object o = id.toString();
                    if (id != null) {
                        properties.put(name, o);
                        feature.put("properties", properties);
                    }
                }
                else {
                  String out = doc.get(name) == null ? "" : doc.get(name).toString();
                  if (out != null) {
                        properties.put(name, out);
                        feature.put("properties", properties);
                    }
                }
            }
            if(cursor.hasNext()) {
                feature.put("type", "Feature");
                features.add(feature);
                featureCollection.put("features", features);
            }

        }
        writer.write(featureCollection.toString());
        writer.flush();
        writer.close();
    }


    public void writeXLSX(MongoCursor<Document> cursor, String path) throws IOException {

        int cellCount = list.size();
        Document d, doc, temp = null;
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
                if(name.contains(".")) {
                    Document t = null;
                    String[] nameParts = name.split("\\p{Punct}");
                    for (int i = 0; i < nameParts.length-1; i++) {
                        doc = (Document) d.get(nameParts[i]);
                        t = (Document) doc.get(nameParts[++i]);
                    }
                    if (t != null) {
                        String a = t.toJson();
                        headerCell.setCellValue(a);
                    }
                } else if (type.equals("oarObject")) {
                    doc = (Document) d.get(name);
                    if (doc != null) {
                        String a = doc.toJson();
                        headerCell.setCellValue(a);
                    }
                } else if (type.equals("geometry")) {
                    doc = (Document) d.get(name);
                    if(doc != null) {
                        headerCell.setCellValue(doc.toJson());
                    }
                } else {
                    String out = d.get(name) == null ? "" : d.get(name).toString();
                    headerCell.setCellValue(out);
                }
            }
        }
        workbook.write(output);
        workbook.close();
    }
}
