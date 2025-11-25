package ch.unisg.mmpmod.processeventemitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

@Service
public class JsonToXesMapper {

    public String convertJsonToXes(String jsonEventString) throws ParserConfigurationException, TransformerException, JsonProcessingException {
        JsonNode jsonEvent = new ObjectMapper().readTree(jsonEventString);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element eventElement = doc.createElement("event");
        doc.appendChild(eventElement);

        Iterator<Map.Entry<String, JsonNode>> fields = jsonEvent.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            Element attrElement;

            if (entry.getValue().isTextual() && key.startsWith("time:")) {
                attrElement = doc.createElement("date");
            } else {
                attrElement = doc.createElement("string");
            }

            attrElement.setAttribute("key", key);
            attrElement.setAttribute("value", entry.getValue().asText());
            eventElement.appendChild(attrElement);
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }
}
