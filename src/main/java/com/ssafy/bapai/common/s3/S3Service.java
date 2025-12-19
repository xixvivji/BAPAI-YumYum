package com.ssafy.bapai.common.s3;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadFile(MultipartFile file, String folderName) throws IOException {
        // 파일명 중복 방지를 위한 UUID 생성
        String originalFileName = file.getOriginalFilename();
        String key = folderName + "/" + UUID.randomUUID() + "_" + originalFileName;

        // S3에 업로드
        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(bucket, key, inputStream,
                    ObjectMetadata.builder().contentType(file.getContentType()).build());
        }

        // 업로드된 파일의 URL 반환
        return s3Template.download(bucket, key).getURL().toString();
    }
}