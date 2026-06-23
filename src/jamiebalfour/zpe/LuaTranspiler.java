package jamiebalfour.zpe;

import jamiebalfour.HelperFunctions;
import jamiebalfour.zpe.core.*;

import java.util.*;

public class LuaTranspiler {

  int indentation = 0;
  boolean inClassDef = false;
  String currentStructureName = null;

  HashMap<String, String> yassToLuaFunctionMapping = new HashMap<>();
  ArrayList<String> usedFunctions = new ArrayList<>();
  ArrayList<String> addedFunctions = new ArrayList<>();
  ArrayList<String> builtInFunctions = new ArrayList<>();
  HashSet<String> currentStructureFields = new HashSet<>();
  HashSet<String> knownMaps = new HashSet<>();
  HashSet<String> knownLists = new HashSet<>();
  HashSet<String> knownStrings = new HashSet<>();

  public String Transpile(IAST code, String s) {

    yassToLuaFunctionMapping.put("std_in", "print");
    yassToLuaFunctionMapping.put("floor", "math.floor");
    yassToLuaFunctionMapping.put("factorial", "math.factorial");
    yassToLuaFunctionMapping.put("time", "os.time");
    yassToLuaFunctionMapping.put("character_to_integer", "string.byte");
    yassToLuaFunctionMapping.put("integer_to_character", "string.char");
    yassToLuaFunctionMapping.put("print_error", "throw_error");

    try {
      for (String fun : HelperFunctions.getResource("/jamiebalfour/zpe/additional_functions_lua.txt", this.getClass()).split("--")) {
        fun = fun.trim();
        String[] lines = fun.split(System.lineSeparator());
        builtInFunctions.add(lines[0]);
      }
    } catch (Exception e) {
      // Ignore
    }



    StringBuilder output = new StringBuilder();
    IAST current = code;
    while (current != null) {
      output.append(innerTranspile(current)).append("\n");
      current = current.next;
    }

    StringBuilder additionalFuncs = new StringBuilder();
    additionalFuncs.append("function string_chars(s)\n")
            .append("  local i = 0\n")
            .append("  return function()\n")
            .append("    i = i + 1\n")
            .append("    if i <= #s then\n")
            .append("      return i, s:sub(i, i)\n")
            .append("    end\n")
            .append("  end\n")
            .append("end\n")
            .append("function yass_each(v)\n")
            .append("  if type(v) == \"string\" then\n")
            .append("    return string_chars(v)\n")
            .append("  end\n")
            .append("  return ipairs(v)\n")
            .append("end\n");
    try {
      for (String fun : HelperFunctions.getResource("/jamiebalfour/zpe/additional_functions_lua.txt", this.getClass()).split("--")) {
        fun = fun.trim();
        String[] lines = fun.split(System.lineSeparator());
        String funcName = lines[0];

        StringBuilder funBuilder = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
          funBuilder.append(lines[i]).append("\n");
        }

        if (usedFunctions.contains(funcName) && !addedFunctions.contains(funcName)) {
          additionalFuncs.append(funBuilder);
        }
      }
    } catch (Exception e) {
      // Ignore
    }

    if (output.toString().contains("function main")) {
      output.append("\nmain()\n");
    }

    return additionalFuncs + output.toString();
  }

  private String addIndentation() {
    return "  ".repeat(indentation);
  }

  private String innerTranspile(IAST n) {
    switch (n.type) {
      case YASSByteCodes.IDENTIFIER: {
        return transpileIdentifier(n);
      }
      case YASSByteCodes.VAR:
      case YASSByteCodes.CONST:
      case YASSByteCodes.VAR_BY_REF: {
        return transpileVar(n);
      }
      case YASSByteCodes.TYPED_PARAMETER: {
        return fixId(n.left.id);
      }
      case YASSByteCodes.BOOL: {
        return n.value.toString().toLowerCase();
      }
      case YASSByteCodes.NULL: {
        return "nil";
      }
      case YASSByteCodes.NEGATIVE: {
        return "-" + innerTranspile((IAST) n.value);
      }
      case YASSByteCodes.INT:
      case YASSByteCodes.DOUBLE: {
        return n.value.toString();
      }
      case YASSByteCodes.STRING: {
        return "\"" + n.value.toString().replace("\"", "\\\"") + "\"";
      }
      case YASSByteCodes.LIST: {
        return "{" + generateParameters((IAST) n.value) + "}";
      }
      case YASSByteCodes.RETURN: {
        return "return " + innerTranspile(n.left);
      }
      case YASSByteCodes.TYPE: {
        return "type (" + innerTranspile((IAST) n.value) + ")";
      }
      case YASSByteCodes.PLUS: {
        return innerTranspile(n.left) + " + " + innerTranspile(n.next);
      }
      case YASSByteCodes.MINUS: {
        return innerTranspile(n.left) + " - " + innerTranspile(n.next);
      }
      case YASSByteCodes.MULT: {
        return innerTranspile(n.left) + " * " + innerTranspile(n.next);
      }
      case YASSByteCodes.DIVIDE: {
        return innerTranspile(n.left) + " / " + innerTranspile(n.next);
      }
      case YASSByteCodes.MODULO: {
        return innerTranspile(n.left) + " % " + innerTranspile(n.next);
      }
      case YASSByteCodes.EQUAL: {
        return innerTranspile(n.left) + " == " + innerTranspile(n.middle);
      }
      case YASSByteCodes.NEQUAL: {
        return innerTranspile(n.left) + " ~= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LT: {
        return innerTranspile(n.left) + " < " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LTE: {
        return innerTranspile(n.left) + " <= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.GT: {
        return innerTranspile(n.left) + " > " + innerTranspile(n.middle);
      }
      case YASSByteCodes.GTE: {
        return innerTranspile(n.left) + " >= " + innerTranspile(n.middle);
      }
      case YASSByteCodes.LAND: {
        return innerTranspile(n.left) + " and " + innerTranspile(n.next);
      }
      case YASSByteCodes.LOR: {
        return innerTranspile(n.left) + " or " + innerTranspile(n.next);
      }
      case YASSByteCodes.ASSIGN: {
        return transpileAssign(n);
      }
      case YASSByteCodes.CONCAT: {
        return "tostring(" + innerTranspile(n.left) + ") .. tostring(" + innerTranspile(n.next) + ")";
      }
      case YASSByteCodes.COUNT: {
        return "#" + innerTranspile(n.left);
      }
      case YASSByteCodes.NEGATION: {
        return "not (" + innerTranspile(((IAST) n.value).next) + ")";
      }
      case YASSByteCodes.DOT: {
        return transpileDotExpression(n);
      }
      case YASSByteCodes.ASSOCIATION: {
        return transpileMap(n);
      }
      case YASSByteCodes.MATCH: {
        return transpileMatch(n);
      }
      case YASSByteCodes.EACH: {
        return transpileForEach(n);
      }
      case YASSByteCodes.INDEX_ACCESSOR: {
        IAST indexNode = unwrapExpression((IAST) n.value);

        if (indexNode.type == YASSByteCodes.STRING) {
          return innerTranspile(n.left) + "[" + innerTranspile(indexNode) + "]";
        }

        String targetName = getSimpleVarName(n.left);

        if (targetName != null && knownMaps.contains(targetName)) {
          return innerTranspile(n.left) + "[" + innerTranspile(indexNode) + "]";
        }

        return innerTranspile(n.left) + "[(" + innerTranspile(indexNode) + ") + 1]";
      }
      case YASSByteCodes.LBRA: {
        return "(" + innerTranspile((IAST) n.value) + ")";
      }
      case YASSByteCodes.EMPTY: {
        return "#" + innerTranspile(n.left) + " == 0";
      }
      case YASSByteCodes.BREAK: {
        return "break";
      }
      case YASSByteCodes.CIRCUMFLEX: {
        return "math.pow(" + innerTranspile(n.left) + ", " + innerTranspile(n.next) + ")";
      }
      case YASSByteCodes.PRE_INCREMENT:
      case YASSByteCodes.POST_INCREMENT: {
        return transpileVar(n) + " = " + transpileVar(n) + " + 1";
      }
      case YASSByteCodes.PRE_DECREMENT:
      case YASSByteCodes.POST_DECREMENT: {
        return transpileVar(n) + " = " + transpileVar(n) + " - 1";
      }
      case YASSByteCodes.FUNCTION: {
        return transpileFunction(n);
      }
      case YASSByteCodes.IF: {
        return transpileIf(n);
      }
      case YASSByteCodes.WHILE: {
        return transpileWhile(n);
      }
      case YASSByteCodes.FOR: {
        return transpileFor(n);
      }
      case YASSByteCodes.FOR_TO: {
        return transpileForTo(n);
      }
      case YASSByteCodes.MODULE: {
        return transpileModule(n);
      }
      case YASSByteCodes.EXPRESSION: {
        return transpileExpression(n);
      }
      case YASSByteCodes.WHEN: {
        return transpileWhen(n);
      }
      case YASSByteCodes.NEW: {
        return transpileNew(n);
      }
      case YASSByteCodes.ARROW_OPERATOR: {
        return transpileArrowOperator(n);
      }

      case YASSByteCodes.STRUCTURE: {
        return transpileStructure(n);
      }

      case YASSByteCodes.THIS: {
        return "self";
      }

      case YASSByteCodes.INFINITE_PARAMETERS: {
        return "...";
      }
      default:
        return "";
    }
  }

  private String transpileExpression(IAST n) {
    return innerTranspile((IAST) n.value);
  }

  private String transpileArrowOperator(IAST n) {
    IAST right = unwrapExpression((IAST) n.value);

    if (n.middle != null && n.middle.type == YASSByteCodes.THIS && right.type == YASSByteCodes.VAR) {
      return "self." + fixId(right.id);
    }

    if (n.middle != null && n.middle.type == YASSByteCodes.THIS && right.type == YASSByteCodes.INDEX_ACCESSOR) {
      return transpileThisIndexAccessor(right);
    }

    if (n.middle != null && n.middle.type == YASSByteCodes.ARROW_OPERATOR && right.type == YASSByteCodes.IDENTIFIER) {
      return transpileArrowOperator(n.middle) + ":" + innerTranspile(right);
    }

    if (right.type == YASSByteCodes.EXPRESSION && right.value instanceof IAST) {
      IAST unwrappedRight = unwrapExpression(right);
      if (unwrappedRight != null && unwrappedRight.type == YASSByteCodes.IDENTIFIER) {
        return innerTranspile(n.middle) + ":" + innerTranspile(unwrappedRight);
      }
    }

    if (right.type == YASSByteCodes.IDENTIFIER) {
      return innerTranspile(n.middle) + ":" + innerTranspile(right);
    } else if (right.type == YASSByteCodes.VAR) {
      return innerTranspile(n.middle) + "." + fixId(right.id);
    } else if (right.type == YASSByteCodes.INDEX_ACCESSOR) {
      return innerTranspile(n.middle) + "." + innerTranspile(right);
    }

    return innerTranspile(n.middle) + "." + innerTranspile(right);
  }

  private String transpileThisIndexAccessor(IAST indexAccessor) {
    if (indexAccessor == null || indexAccessor.type != YASSByteCodes.INDEX_ACCESSOR) {
      return "self";
    }

    String target;

    if (indexAccessor.left != null &&
            (indexAccessor.left.type == YASSByteCodes.VAR ||
                    indexAccessor.left.type == YASSByteCodes.CONST ||
                    indexAccessor.left.type == YASSByteCodes.VAR_BY_REF)) {
      target = "self." + fixId(indexAccessor.left.id);
    } else {
      target = "self." + innerTranspile(indexAccessor.left);
    }

    IAST indexNode = (IAST) indexAccessor.value;

    if (indexNode.type == YASSByteCodes.STRING) {
      return target + "[" + innerTranspile(indexNode) + "]";
    }

    return target + "[(" + innerTranspile(indexNode) + ") + 1]";
  }

private String transpileNew(IAST n){
  return fixId(n.id) + "(" +
          generateParameters((IAST)n.value) +
          ")";
}

  private String transpileStructure(IAST n) {
    String oldStructureName = currentStructureName;
    boolean oldInClassDef = inClassDef;

    currentStructureName = fixId(n.id);
    inClassDef = true;
    currentStructureFields.clear();

    StringBuilder output = new StringBuilder();

    output.append("function ")
            .append(currentStructureName)
            .append("(");

    IAST current = (IAST) n.value;
    IAST constructor = null;

    while (current != null) {
      if (current.type == YASSByteCodes.FUNCTION && "_construct".equals(fixId(current.id))) {
        constructor = current;
        output.append(generateParameters((IAST) current.value));
        break;
      }
      current = current.next;
    }

    output.append(")")
            .append(System.lineSeparator());

    indentation++;
    output.append(addIndentation()).append("local self = {}").append(System.lineSeparator());

    current = (IAST) n.value;
    while (current != null) {
      if (current.type == YASSByteCodes.VAR || current.type == YASSByteCodes.CONST || current.type == YASSByteCodes.VAR_BY_REF) {
        currentStructureFields.add(fixId(current.id));
        output.append(addIndentation())
                .append("self.")
                .append(fixId(current.id))
                .append(" = nil")
                .append(System.lineSeparator());
      } else if (current.type == YASSByteCodes.ASSIGN && current.middle != null &&
              (current.middle.type == YASSByteCodes.VAR || current.middle.type == YASSByteCodes.CONST || current.middle.type == YASSByteCodes.VAR_BY_REF)) {
        currentStructureFields.add(fixId(current.middle.id));
        output.append(addIndentation())
                .append("self.")
                .append(fixId(current.middle.id))
                .append(" = ")
                .append(innerTranspile((IAST) current.value))
                .append(System.lineSeparator());
      }
      current = current.next;
    }

    current = (IAST) n.value;
    while (current != null) {
      if (current.type == YASSByteCodes.FUNCTION && !"_construct".equals(fixId(current.id))) {
        output.append(addIndentation())
                .append(transpileFunction(current))
                .append(System.lineSeparator());
      }
      current = current.next;
    }

    if (constructor != null) {
      current = constructor.left;
      while (current != null) {
        output.append(addIndentation())
                .append(innerTranspile(current))
                .append(System.lineSeparator());
        current = current.next;
      }
    }

    output.append(addIndentation()).append("return self").append(System.lineSeparator());
    indentation--;
    output.append("end").append(System.lineSeparator());

    currentStructureName = oldStructureName;
    inClassDef = oldInClassDef;

    return output.toString();
  }

  private String transpileModule(IAST n) {

    StringBuilder output = new StringBuilder();

    IAST current = (IAST) n.value;
    while (current != null) {
      output.append(innerTranspile(current)).append(System.lineSeparator());
      current = current.next;
    }

    return output.toString();
  }

  private String generateParameters(IAST n) {
    StringBuilder output = new StringBuilder();
    IAST current = n;
    while (current != null) {
      if(current.type == YASSByteCodes.INFINITE_PARAMETERS){
        output.append("...");
      }
      else{
        output.append(innerTranspile(current));
      }
      current = current.next;
      if (current != null) output.append(", ");
    }
    return output.toString();
  }

  private String getTempMapNameFromPair(IAST n) {
    return "";
  }

  private String transpileIdentifier(IAST n) {
    String id = fixId(n.id);
    if ("map_create_ordered".equals(id) && n.value != null) {
      IAST current = (IAST) n.value;
      while (current != null) {
        knownMaps.add(getTempMapNameFromPair(current));
        current = current.next;
      }
    }
    if (id.equals("list_get_length")) {
      return "#" + generateParameters((IAST) n.value);
    }
    if (yassToLuaFunctionMapping.containsKey(id)) {
      id = yassToLuaFunctionMapping.get(id);
    }
    if (ZPEKit.internalFunctionExists(id) || builtInFunctions.contains(id)) {
      usedFunctions.add(id);
    }
    return id + "(" + generateParameters((IAST) n.value) + ")";
  }

  private String transpileVar(IAST n) {
    String id = fixId(n.id);

    if (inClassDef && currentStructureFields.contains(id)) {
      return "self." + id;
    }

    return id;
  }

  private String transpileFunction(IAST n) {
    String id = fixId(n.id);
    addedFunctions.add(id);

    HashSet<String> oldKnownMaps = new HashSet<>(knownMaps);
    HashSet<String> oldKnownLists = new HashSet<>(knownLists);
    HashSet<String> oldKnownStrings = new HashSet<>(knownStrings);
    rememberTypedParameters((IAST) n.value);

    String params = generateParameters((IAST) n.value);

    StringBuilder output = new StringBuilder();

    if (inClassDef && currentStructureName != null) {
      if ("_construct".equals(id)) {
        return "";
      }

      output.append("function self:")
              .append(id)
              .append("(")
              .append(params)
              .append(")\n");
    } else {
      output.append("function ")
              .append(id)
              .append("(")
              .append(params)
              .append(")\n");
    }

    indentation++;

    String infiniteParameterName = getInfiniteParameterName((IAST) n.value);
    if (infiniteParameterName != null && !infiniteParameterName.isEmpty()) {
      output.append(addIndentation())
              .append("local ")
              .append(infiniteParameterName)
              .append(" = {...}\n");
      knownLists.add(infiniteParameterName);
    }

    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }
    indentation--;
    output.append(addIndentation()).append("end\n");

    knownMaps.clear();
    knownMaps.addAll(oldKnownMaps);
    knownLists.clear();
    knownLists.addAll(oldKnownLists);
    knownStrings.clear();
    knownStrings.addAll(oldKnownStrings);

    return output.toString();
  }

  private String transpileIf(IAST n) {
    StringBuilder output = new StringBuilder(
            "if " + innerTranspile((IAST) n.value) + " then\n");

    indentation++;

    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation())
              .append(innerTranspile(current))
              .append("\n");
      current = current.next;
    }

    indentation--;

    IAST currentInner = n.middle;
    while (currentInner != null) {
      if (currentInner.type == YASSByteCodes.ELSEIF) {
        output.append(addIndentation())
                .append("elseif ")
                .append(innerTranspile((IAST) currentInner.value))
                .append(" then\n");

        indentation++;

        current = currentInner.left;
        while (current != null) {
          output.append(addIndentation())
                  .append(innerTranspile(current))
                  .append("\n");
          current = current.next;
        }

        indentation--;
      } else if (currentInner.type == YASSByteCodes.ELSE) {
        output.append(addIndentation()).append("else\n");

        indentation++;

        current = currentInner.left;
        while (current != null) {
          output.append(addIndentation())
                  .append(innerTranspile(current))
                  .append("\n");
          current = current.next;
        }

        indentation--;
      }

      currentInner = currentInner.next;
    }

    output.append(addIndentation()).append("end");

    return output.toString();
  }

  private String transpileWhile(IAST n) {
    StringBuilder output = new StringBuilder("while " + innerTranspile((IAST) n.value) + " do\n");
    indentation++;
    IAST current = n.left;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }
    indentation--;
    output.append(addIndentation()).append("end");
    return output.toString();
  }

  private String transpileFor(IAST n) {
    String var = innerTranspile(n.middle.left.middle);
    String start = innerTranspile((IAST) n.middle.left.value);
    String end = innerTranspile(((IAST) ((IAST) n.value).value).middle);
    String step = (n.middle.value instanceof String) ? (", " + n.middle.value) : "";
    StringBuilder output = new StringBuilder("for " + var + " = " + start + ", " + end + step + " do\n");
    indentation++;
    IAST current = n.left.next;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }
    indentation--;
    output.append("end");
    return output.toString();
  }

  private String transpileForTo(IAST n) {
    String var = innerTranspile(n.middle.left.middle);
    String start = innerTranspile((IAST) n.middle.left.value);
    String end = innerTranspile((IAST) ((IAST) n.value).value);
    StringBuilder output = new StringBuilder("for " + var + " = " + start + ", " + end + " do\n");
    indentation++;
    IAST current = n.left.next;
    while (current != null) {
      output.append(addIndentation()).append(innerTranspile(current)).append("\n");
      current = current.next;
    }
    indentation--;
    output.append("end");
    return output.toString();
  }

  private String transpileAssign(IAST n) {
    rememberAssignmentType(n.middle, (IAST) n.value);
    return transpileAssignmentTarget(n.middle) + " = " + innerTranspile((IAST) n.value);
  }

  private String transpileAssignmentTarget(IAST target) {
    if (target == null) {
      return "";
    }

    if (target.type == YASSByteCodes.VAR ||
            target.type == YASSByteCodes.CONST ||
            target.type == YASSByteCodes.VAR_BY_REF) {

      String id = fixId(target.id);

      if (inClassDef && currentStructureFields.contains(id)) {
        return "self." + id;
      }

      return id;
    }

    return innerTranspile(target);
  }

  private String transpileDotExpression(IAST n) {
    String method = ((IAST) n.value).id;

    switch (method) {
      case "enqueue":
      case "dequeue":
      case "push":
      case "pop":
        return innerTranspile(n.left)
                + ":"
                + innerTranspile((IAST)n.value);
      case "put":
        usedFunctions.add("_put");
        return "_put(" + innerTranspile(n.left) + ", " + generateParameters((IAST) ((IAST) n.value).value) + ")";

      case "append":
        usedFunctions.add("_append");
        return "_append(" + innerTranspile(n.left) + ", " + generateParameters((IAST) ((IAST) n.value).value) + ")";

      case "length":
        return "#" + innerTranspile(n.left);

      default:
        IAST right = (IAST) n.value;

        if (right.type == YASSByteCodes.IDENTIFIER) {
          return innerTranspile(n.left) + ":" + innerTranspile(right);
        }

        return innerTranspile(n.left) + "." + innerTranspile(right);
    }
  }

  private String transpileMap(IAST n) {
    StringBuilder output = new StringBuilder();
    output.append("{");

    IAST current = (IAST) n.value;
    while (current != null) {
      output.append("[")
              .append(innerTranspile(current))
              .append("] = ");

      current = current.next;

      output.append(innerTranspile(current));

      current = current.next;

      if (current != null) {
        output.append(", ");
      }
    }

    output.append("}");
    return output.toString();
  }

  private String transpileMatch(IAST n) {
    StringBuilder output = new StringBuilder();
    output.append("({");

    IAST current = n.left;
    while (current != null) {
      output.append("[")
              .append(innerTranspile(current.left))
              .append("] = ")
              .append(innerTranspile(current.middle));

      current = current.next;

      if (current != null) {
        output.append(", ");
      }
    }

    output.append("})[")
            .append(innerTranspile((IAST) n.value))
            .append("]");

    return output.toString();
  }

  private String transpileForEach(IAST n) {
    String variable;

    if (n.left.middle.type == YASSByteCodes.VAR) {
      Object value = n.left.middle.value;

      if (value != null) {
        variable = fixId(value.toString());
      } else {
        variable = transpileVar(n.left.middle);
      }
    } else {
      variable = innerTranspile(n.left.middle);
    }

    StringBuilder output = new StringBuilder();
    output.append("for _, ")
            .append(variable)
            .append(" in yass_each(")
            .append(innerTranspile(n.left.left))
            .append(") do\n");

    indentation++;

    IAST current = n.middle;
    while (current != null) {
      output.append(addIndentation())
              .append(innerTranspile(current))
              .append("\n");
      current = current.next;
    }

    indentation--;

    output.append(addIndentation()).append("end");
    return output.toString();
  }

  private String fixId(String id) {
    if(id.contains("$")) id = id.replace("$", "");
    if (id.contains("/")) id = id.replace("/", "_");
    if (id.contains("::")) id = id.substring(id.indexOf("::") + 2);
    if (id.contains("~")) id = id.substring(id.indexOf("~") + 1);
    return id;
  }

  private String transpileWhen(IAST n) {
    StringBuilder output = new StringBuilder();

    String id = fixId(n.id);

    IAST currentSelect = (IAST) n.value;
    boolean first = true;

    while (currentSelect != null) {
      output.append(addIndentation())
              .append(first ? "if " : "elseif ")
              .append(id)
              .append(" == ")
              .append(transpileWhenValue(currentSelect.value))
              .append(" then\n");

      indentation++;

      IAST current = currentSelect.left;
      if (current == null) {
        output.append(addIndentation()).append("-- pass\n");
      }

      while (current != null) {
        output.append(addIndentation())
                .append(innerTranspile(current))
                .append("\n");
        current = current.next;
      }

      indentation--;

      first = false;
      currentSelect = currentSelect.next;
    }

    if (n.middle != null) {
      output.append(addIndentation()).append("else\n");

      indentation++;

      IAST current = n.middle;
      while (current != null) {
        output.append(addIndentation())
                .append(innerTranspile(current))
                .append("\n");
        current = current.next;
      }

      indentation--;
    }

    output.append(addIndentation()).append("end");

    return output.toString();
  }

  private String transpileWhenValue(Object value) {
    if (value == null) {
      return "nil";
    }

    String text = value.toString();

    if (text.equalsIgnoreCase("true")) {
      return "true";
    }

    if (text.equalsIgnoreCase("false")) {
      return "false";
    }

    if (text.equalsIgnoreCase("null")) {
      return "nil";
    }

    if (isLuaNumber(text)) {
      return text;
    }

    return "\"" + escapeLuaString(text) + "\"";
  }

  private boolean isLuaNumber(String text) {
    if (text == null || text.trim().isEmpty()) {
      return false;
    }

    try {
      Double.parseDouble(text);
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private String escapeLuaString(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private IAST unwrapExpression(IAST n) {
    while (n != null &&
            (n.type == YASSByteCodes.EXPRESSION || n.type == YASSByteCodes.LBRA) &&
            n.value instanceof IAST) {
      n = (IAST) n.value;
    }

    return n;
  }

  private String getSimpleVarName(IAST n) {
    n = unwrapExpression(n);

    if (n == null) {
      return null;
    }

    if (n.type == YASSByteCodes.VAR ||
            n.type == YASSByteCodes.CONST ||
            n.type == YASSByteCodes.VAR_BY_REF) {
      return fixId(n.id);
    }

    return null;
  }

  private void rememberAssignmentType(IAST target, IAST value) {
    String targetName = getSimpleVarName(target);

    if (targetName == null || value == null) {
      return;
    }

    IAST unwrappedValue = unwrapExpression(value);

    if (unwrappedValue.type == YASSByteCodes.ASSOCIATION) {
      knownMaps.add(targetName);
      return;
    }

    if (unwrappedValue.type == YASSByteCodes.LIST) {
      knownLists.add(targetName);
      knownMaps.remove(targetName);
      knownStrings.remove(targetName);
      return;
    }

    if (unwrappedValue.type == YASSByteCodes.STRING) {
      knownStrings.add(targetName);
      knownMaps.remove(targetName);
      knownLists.remove(targetName);
      return;
    }

    if (unwrappedValue.type == YASSByteCodes.IDENTIFIER &&
            "map_create_ordered".equals(fixId(unwrappedValue.id))) {
      knownMaps.add(targetName);
    }

    knownLists.remove(targetName);
    knownStrings.remove(targetName);
  }

  private void rememberTypedParameters(IAST params) {
    IAST current = params;

    while (current != null) {
      if (current.type == YASSByteCodes.TYPED_PARAMETER && current.left != null) {
        String name = getSimpleVarName(current.left);
        String typeInfo = "";

        if (current.id != null) {
          typeInfo += current.id.toLowerCase() + " ";
        }
        if (current.value != null) {
          typeInfo += current.value.toString().toLowerCase() + " ";
        }
        if (current.middle != null) {
          typeInfo += current.middle.toString().toLowerCase() + " ";
        }

        if (name != null) {
          if (typeInfo.contains("map")) {
            knownMaps.add(name);
          } else if (typeInfo.contains("list")) {
            knownLists.add(name);
          } else if (typeInfo.contains("string")) {
            knownStrings.add(name);
          }
        }
      }

      current = current.next;
    }
  }

  private String getInfiniteParameterName(IAST params) {
    IAST current = params;

    while (current != null) {
      if (current.type == YASSByteCodes.INFINITE_PARAMETERS) {
        if (current.id != null) {
          return fixId(current.id);
        }
        if (current.value != null) {
          return fixId(current.value.toString());
        }
      }

      current = current.next;
    }

    return null;
  }
}