import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DepartmentService } from '../../../core/services/department';
import { Department } from '../../../core/models/department.model';

@Component({
  selector: 'app-department-list',
  imports: [ReactiveFormsModule],
  templateUrl: './department-list.html',
  styleUrl: './department-list.scss',
})
export class DepartmentList implements OnInit {
  private readonly departmentService = inject(DepartmentService);
  private readonly fb = inject(FormBuilder);

  readonly departments = signal<Department[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
    costCenter: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.departmentService.findAll().subscribe({
      next: (departments) => {
        this.departments.set(departments);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.errorMessage.set(null);
    this.departmentService.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.form.reset();
        this.load();
      },
      error: () => this.errorMessage.set('Could not create the department (code may already be in use).'),
    });
  }
}
