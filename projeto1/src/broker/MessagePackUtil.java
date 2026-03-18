import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MessagePackUtil {
    // MessagePack (binário)
    private static final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());
    
    public static byte[] serialize(Object obj) throws Exception {
        return mapper.writeValueAsBytes(obj);
    }
    
    public static <T> T deserialize(byte[] data, Class<T> clazz) throws Exception {
        return mapper.readValue(data, clazz);
    }
}
