package com.enterpriseapp.procureflow.department;

import com.enterpriseapp.procureflow.common.exception.DuplicateResourceException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.department.dto.DepartmentRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

  private final DepartmentRepository departmentRepository;

  public List<Department> findAll() {
    return departmentRepository.findAll();
  }

  public Department findById(Long id) {
    return departmentRepository
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("Department", id));
  }

  @Transactional
  public Department create(DepartmentRequest request) {
    if (departmentRepository.existsByCode(request.code())) {
      throw new DuplicateResourceException(
          "A department with code " + request.code() + " already exists");
    }
    Department department =
        Department.builder()
            .code(request.code())
            .name(request.name())
            .costCenter(request.costCenter())
            .managerUserId(request.managerUserId())
            .build();
    return departmentRepository.save(department);
  }

  @Transactional
  public Department update(Long id, DepartmentRequest request) {
    Department department = findById(id);
    department.setName(request.name());
    department.setCostCenter(request.costCenter());
    department.setManagerUserId(request.managerUserId());
    return department;
  }

  @Transactional
  public void delete(Long id) {
    if (!departmentRepository.existsById(id)) {
      throw ResourceNotFoundException.of("Department", id);
    }
    departmentRepository.deleteById(id);
  }
}
