import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;


public class Main {

    public static void main(String[] args) throws IOException, ParseException {
        Exporter ex = new Exporter();
        MongoCursor <Document> cursor =  ex.getData();
        //MongoCollection<Document> collection = ex.connect();
        //MongoCursor <Document> cursor  = ex.sort(collection);
        //ex.writeJson(cursor);
       // ex.writeXLSX(cursor);


    }

}