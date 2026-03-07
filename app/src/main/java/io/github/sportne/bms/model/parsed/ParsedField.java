package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

public record ParsedField(
    String name, String typeName, Integer length, Endian endian, String fixed, String comment) {

  public ParsedField {
    name = Objects.requireNonNull(name, "name");
    typeName = Objects.requireNonNull(typeName, "typeName");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
