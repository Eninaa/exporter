import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Main {

    public static void main(String[] args) throws IOException, ParseException {
      //Exporter ex = new Exporter();
      // ex.getData();
       File file = new File("testtt.geojson");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        Document doc, d;
        int cellCount = 3;

        JSONObject featureCollection = new JSONObject();
        featureCollection.put("type", "FeatureCollection");

        JSONArray features = new JSONArray();
        JSONObject feature = new JSONObject();
        JSONObject properties = new JSONObject();
        JSONObject geometry = new JSONObject();

        feature.put("type", "Feature");
        features.add(feature);
        int i = 2;

        while(i > 0) {

            for (int j = 0; j < cellCount; j++) {
                geometry.put("type", "t");
                geometry.put("coor", j);

                System.out.println(j + " " + geometry);
                feature.put("geometry", geometry);

                properties.put("prop1", j);
                feature.put("properties", properties);
                feature.put("type", "Feature");
                features.add(feature);
            }

           /* if(i > 0) {

                features.add(feature);
            }*/
            i--;
        }
        featureCollection.put("features", features);
        System.out.println(featureCollection);
        writer.write(featureCollection.toString());

        writer.flush();
        writer.close();
    }

}