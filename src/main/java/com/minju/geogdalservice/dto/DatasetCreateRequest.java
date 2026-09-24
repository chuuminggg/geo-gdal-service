package com.minju.geogdalservice.dto;

import com.minju.geogdalservice.entity.DatasetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasetCreateRequest {

    // S3 키·URL 경로에 쓰이므로 소문자/숫자/하이픈만 허용
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "name은 소문자, 숫자, 하이픈(-)만 사용할 수 있습니다.")
    private String name;

    @NotNull
    private DatasetType type;

    @Size(max = 500)
    private String description;
}
