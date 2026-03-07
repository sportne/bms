package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to a reusable blob-vector definition declared at schema scope.
 *
 * @param blobVectorTypeName resolved blob-vector type name
 */
public record BlobVectorTypeRef(String blobVectorTypeName) implements ResolvedTypeRef {

  /**
   * Creates a reference to a reusable blob-vector type.
   *
   * @param blobVectorTypeName reusable blob-vector type name
   */
  public BlobVectorTypeRef {
    blobVectorTypeName = Objects.requireNonNull(blobVectorTypeName, "blobVectorTypeName");
  }
}
