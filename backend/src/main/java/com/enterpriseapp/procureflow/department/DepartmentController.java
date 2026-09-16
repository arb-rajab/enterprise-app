package com.enterpriseapp.procureflow.department;

import com.enterpriseapp.procureflow.department.dto.DepartmentRequest;
import com.enterpriseapp.procureflow.department.dto.DepartmentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departmentService;

  @GetMapping
  public List<DepartmentResponse> findAll() {
    return departmentService.findAll().stream().map(DepartmentResponse::from).toList();
  }

  @GetMapping("/{id}")
  public DepartmentResponse findById(@PathVariable Long id) {
    return DepartmentResponse.from(departmentService.findById(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
    Department created = departmentService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/departments/" + created.getId()))
        .body(DepartmentResponse.from(created));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public DepartmentResponse update(
      @PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
    return DepartmentResponse.from(departmentService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    departmentService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
