import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonObject;
import nl.davidengelen.redsquare.saito.SaitoRegistry;
import nl.davidengelen.redsquare.saito.SaitoRegisteredName;
public final class VerifyRegistry {
  public static void main(String[] args) throws Exception {
    String body=Files.readString(Path.of(args[0]));
    JsonObject output=new JsonObject();
    output.addProperty("verifier","existing compiled core SaitoRegistry.readNamecheck");
    output.addProperty("androidAppExecution",false);
    try {
      SaitoRegisteredName name=SaitoRegistry.INSTANCE.readNamecheck(body,"Saitoverse@saito",SaitoRegistry.REGISTRAR);
      output.addProperty("outcome",name==null?"empty-success":(name.getActive()?"verified-active":"verified-inactive"));
      if(name!=null){output.addProperty("identifier",name.getIdentifier());output.addProperty("publicKey",name.getPublicKey());}
    } catch(Exception failure) {
      output.addProperty("outcome","rejected");output.addProperty("exceptionClass",failure.getClass().getSimpleName());
    }
    System.out.println(output);
  }
}
