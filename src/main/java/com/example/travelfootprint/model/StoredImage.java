package com.example.travelfootprint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stored_image", indexes = {
        @Index(name = "idx_stored_image_public_path", columnList = "public_path", unique = true)
})
public class StoredImage extends BaseEntity {

    @Column(name = "public_path", nullable = false, unique = true, length = 255)
    private String publicPath;

    @Column(nullable = false, length = 64)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 25 * 1024 * 1024)
    private byte[] content;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
