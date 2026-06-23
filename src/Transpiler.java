import jamiebalfour.zpe.LuaTranspiler;
import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.interfaces.ZPESyntaxTranspiler;

public class Transpiler implements ZPESyntaxTranspiler {

  @Override
  public String transpilerName() {
    return "ZenLua";
  }

  @Override
  public String transpile(IAST code, String s) {

    return new LuaTranspiler().Transpile(code, s);

  }

  @Override
  public String getLanguageName() {
    return "Lua";
  }

  @Override
  public String getFileExtension() {
    return "lua";
  }
}