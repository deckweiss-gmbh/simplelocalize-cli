package io.simplelocalize.cli.util;

import net.minidev.json.JSONStyle;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;

public class JsonUtil {

  private JsonUtil() {
  }

  public static void removeEmptyFields(HashMap map) {
    map.values().removeIf(e->e instanceof String && ((String) e).isEmpty());
    for (Object value: map.values()) {
      if (value instanceof HashMap) {
        // Apply a recursion on inner maps
        removeEmptyFields((HashMap) value);
      }
    }
  }

  public static class PrettyJSONStyle extends JSONStyle {
    private int level = 0;
    private String indentString = "  ";

    public PrettyJSONStyle() {
    }

    private void indent(Appendable out) throws IOException, IOException {
      out.append('\n');
      out.append(StringUtils.repeat(indentString, level));
    }

    @Override
    public void objectStart(Appendable out) throws IOException {
      super.objectStart(out);
      level++;
    }

    @Override
    public void objectStop(Appendable out) throws IOException {
      level--;
      indent(out);
      super.objectStop(out);
    }

    @Override
    public void objectNext(Appendable out) throws IOException {
      super.objectNext(out);
      indent(out);
    }

    @Override
    public void objectEndOfKey(Appendable out) throws IOException {
      super.objectEndOfKey(out);
      out.append(' ');
    }

    @Override
    public void objectFirstStart(Appendable out) throws IOException {
      indent(out);
      super.objectFirstStart(out);
    }

    @Override
    public void arrayfirstObject(Appendable out) throws IOException {
      indent(out);
      super.arrayfirstObject(out);
    }

    @Override
    public void arrayNextElm(Appendable out) throws IOException {
      super.arrayNextElm(out);
      indent(out);
    }

    @Override
    public void arrayStart(Appendable out) throws IOException {
      super.arrayStart(out);
      level++;
    }

    @Override
    public void arrayStop(Appendable out) throws IOException {
      level--;
      indent(out);
      super.arrayStop(out);
    }

  }
}
