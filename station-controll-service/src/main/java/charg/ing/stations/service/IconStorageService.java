package charg.ing.stations.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
@Slf4j
public class IconStorageService {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    private static final String ICONS_DIR = "connector-icons";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "svg");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @PostConstruct
    public void init() {
        try {
            Path uploadDir = Paths.get(uploadPath, ICONS_DIR);
            Files.createDirectories(uploadDir);
            log.info("Upload directory created: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to create upload directory", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }

    public Mono<String> saveIcon(FilePart filePart, Integer entityId) {
        return Mono.defer(() -> {
            validateFilePart(filePart);
            String fileName = generateFileName(filePart.filename(), entityId);
            Path filePath = getIconPath(fileName);
            return filePart.transferTo(filePath).thenReturn("/uploads/" + ICONS_DIR + "/" + fileName);
        });
    }

    public Mono<Void> deleteIcon(String iconUrl) {
        return Mono.defer(() -> {
            if (iconUrl == null || !iconUrl.startsWith("/uploads/")) {
                return Mono.empty();
            }
            try {
                String relativePath = iconUrl.substring("/uploads/".length());
                Path filePath = Paths.get(uploadPath, relativePath);
                Files.deleteIfExists(filePath);
                log.info("Deleted icon: {}", filePath);
                return Mono.empty();
            } catch (IOException e) {
                log.warn("Failed to delete icon: {}", iconUrl, e);
                return Mono.empty(); // не прерываем поток
            }
        }).then();
    }

    private void validateFilePart(FilePart filePart) {
        // В FilePart нет методов getSize() и getContentType() напрямую.
        // Проверки размера и типа лучше делать в контроллере, так как здесь у нас только FilePart.
        // Можно попробовать получить Content-Type из заголовков, но для простоты оставим только расширение.
        String filename = filePart.filename();
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("File name is empty");
        }
        String extension = getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed: " + ALLOWED_EXTENSIONS);
        }
        // Ограничение размера не проверяется, так как FilePart не предоставляет размер.
        // Можно добавить проверку в контроллере после получения байтов, но лучше довериться лимитам Spring.
    }

    private String generateFileName(String originalFilename, Integer entityId) {
        String extension = getFileExtension(originalFilename);
        return String.format("connector_%d_%d.%s",
                entityId, System.currentTimeMillis(), extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private Path getIconPath(String fileName) {
        return Paths.get(uploadPath, ICONS_DIR, fileName);
    }
}