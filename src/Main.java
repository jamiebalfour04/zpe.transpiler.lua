import java.io.IOException;

public class Main {
  public static void main(String[] args) {
    System.out.println("YASS to Lua transpiler for ZPE");
    if(args.length == 2){
      test(args[0], args[1]);
    }

  }

  private static void test(String input, String output){

    try {
      String s = jamiebalfour.zpe.core.ZPEKit.convertCode(jamiebalfour.FileHelperFunctions.readFileAsString(input), "output", new Transpiler());
      System.out.println(s);
      jamiebalfour.FileHelperFunctions.writeFile(output, s, false);
      System.out.println(jamiebalfour.HelperFunctions.shellExec("lua " + output));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
