package cm.cirt.bts.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Email
    private String email;

    @Pattern(regexp = "ROLE_USER|ROLE_ADMIN", message = "role must be ROLE_USER or ROLE_ADMIN")
    private String role;

    private Boolean enabled;
}
