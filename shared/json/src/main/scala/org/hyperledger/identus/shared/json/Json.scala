package org.hyperledger.identus.shared.json

import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.http.media.MediaType
import com.apicatalog.jsonld.loader.{DocumentLoader, HttpLoader, LRUDocumentCache}
import com.apicatalog.jsonld.JsonLd
import com.apicatalog.rdf.Rdf
import io.setl.rdf.normalization.RdfNormalize
import org.erdtman.jcs.JsonCanonicalizer

import java.io.{ByteArrayInputStream, IOException, StringWriter}
import java.nio.charset.StandardCharsets
import scala.util.Try

object Json {

  /** Canonicalizes a JSON string to JCS format according to RFC 8785
    *
    * @param jsonStr
    *   JSON string to canonicalize
    * @return
    *   canonicalized JSON string
    */
  def canonicalizeToJcs(jsonStr: String): Either[IOException, String] =
    try {
      Right(new JsonCanonicalizer(jsonStr).getEncodedString)
    } catch case exception: IOException => Left(exception)

  /** Canonicalizes a JSON-LD string to RDF according to the Universal RDF Dataset Normalization Algorithm 2015
    *
    * @param jsonLdStr
    *   JSON-LD string to canonicalize
    * @return
    *   canonicalized RDF as a byte array
    */
  def canonicalizeJsonLDoRdf(jsonLdStr: String): Either[Throwable, Array[Byte]] = {
    Try {
      val inputStream = new ByteArrayInputStream(jsonLdStr.getBytes)
      val document = JsonDocument.of(inputStream)
      val rdfDataset = JsonLd.toRdf(document).loader(lruDocumentLoader).get
      val normalized = RdfNormalize.normalize(rdfDataset)
      val writer = new StringWriter
      val rdfWriter = Rdf.createWriter(MediaType.N_QUADS, writer)
      rdfWriter.write(normalized)
      val bytes = writer.toString.getBytes(StandardCharsets.UTF_8);
      bytes
    }.toEither
  }

  lazy val lruDocumentLoader: DocumentLoader = new LRUDocumentCache(HttpLoader.defaultInstance(), 1024)
}
