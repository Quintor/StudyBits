package nl.quintor.studybits.validator;

import nl.quintor.studybits.entity.Student;
import nl.quintor.studybits.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class StudentValidator implements Validator {
    @Autowired
    private StudentService studentService;

    @Override
    public boolean supports(Class<?> aClass) {
        return Student.class.equals(aClass);
    }

    @Override
    public void validate(Object o, Errors errors) {
        Student student = (Student) o;

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "username", "NotEmpty");

        if (studentService.getStudentByStudentId(student.getStudentId()) != null) {
            errors.rejectValue("id", "Student id is duplicate");
        }

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "NotEmpty");
        if (student.getPassword().length() < 8 || student.getPassword().length() > 32) {
            errors.rejectValue("password", "Password must be at least 8 characters");
        }
    }
}