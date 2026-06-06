package fr.traqueur.example.config;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultBool;
import fr.traqueur.structura.annotations.defaults.DefaultInt;
import fr.traqueur.structura.annotations.defaults.DefaultString;
import fr.traqueur.structura.api.Loadable;

/**
 * Storage backend config — storage.yml
 *
 * The backend is polymorphic with inline=true: the "type" key appears at
 * the root of the file, not nested inside a "backend" block.
 *
 * Local example:
 *   type: local
 *   backend:
 *     path: ./data
 *     max-file-size-mb: 100
 *
 * S3 example:
 *   type: s3
 *   backend:
 *     bucket: my-bucket
 *     region: eu-west-1
 *     access-key: AKIAIOSFODNN7EXAMPLE
 *     secret-key: wJalrXUtnFEMI
 *     use-ssl: true
 */
public record StorageConfig(
    StorageBackend backend
) implements Loadable {

    @Polymorphic(key = "type", inline = true)
    public interface StorageBackend extends Loadable {}

    public record LocalBackend(
        @DefaultString("./data") String path,
        @DefaultInt(100)         int    maxFileSizeMb
    ) implements StorageBackend {}

    public record S3Backend(
        @DefaultString("my-bucket")    String  bucket,
        @DefaultString("eu-west-1")    String  region,
        @DefaultString("CHANGE_ME")    String  accessKey,
        @DefaultString("CHANGE_ME")    String  secretKey,
        @DefaultBool(true)             boolean useSsl
    ) implements StorageBackend {}
}
