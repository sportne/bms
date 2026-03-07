package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable blob-array definition declared at schema scope.
 *
 * @param blobArrayTypeName resolved blob-array type name
 */
public record BlobArrayTypeRef(String blobArrayTypeName) implements ResolvedTypeRef {

  /**
   * Creates a reference to a reusable blob-array type.
   *
   * @param blobArrayTypeName reusable blob-array type name
   */
  public BlobArrayTypeRef {
    blobArrayTypeName = Objects.requireNonNull(blobArrayTypeName, "blobArrayTypeName");
  }
}
