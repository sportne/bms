package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parse callback for one message member element. */
@FunctionalInterface
interface MessageMemberHandler {

  /**
   * Parses one message member element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @return parsed message member
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if parsing fails
   */
  ParsedMessageMember parse(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException;
}
