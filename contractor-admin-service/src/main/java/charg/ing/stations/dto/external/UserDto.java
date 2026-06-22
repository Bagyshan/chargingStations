package charg.ing.stations.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String role;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
