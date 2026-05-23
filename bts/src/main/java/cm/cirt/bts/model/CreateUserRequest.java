package cm.cirt.bts.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 80)
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6, max = 120)
    private String password;

    @Pattern(regexp = "ROLE_USER|ROLE_ADMIN", message = "role must be ROLE_USER or ROLE_ADMIN")
    private String role = "ROLE_USER";
}
