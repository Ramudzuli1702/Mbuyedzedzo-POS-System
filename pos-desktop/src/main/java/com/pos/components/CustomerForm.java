package com.pos.components;

import com.pos.models.Customer;
import com.pos.services.CustomerService;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import java.util.function.Consumer;

/** The same customer form is used from Customers and the sales counter. */
public final class CustomerForm {
    private CustomerForm() {}
    public static Dialog<Void> create(int staff,CustomerService service,Consumer<Customer> onSaved) {
        TextField name=new TextField(),email=new TextField(),phone=new TextField();
        DatePicker birth=new DatePicker();
        name.setPromptText("Full name");email.setPromptText("name@example.com");phone.setPromptText("Contact number");
        var contact=Ui.formGrid();
        Ui.formField(contact,0,"Full name",name);Ui.formField(contact,1,"Email address",email);Ui.formField(contact,2,"Phone number",phone);
        var personal=Ui.formGrid();Ui.formField(personal,0,"Date of birth",birth);
        return Ui.createForm("Add customer","New customer",new VBox(16,
                Ui.section("Contact information",contact),
                Ui.section("Personal information",personal,new Label("Customers must be at least 18 years old."))),"Add customer",()->{
            if(name.getText().isBlank() || !email.getText().contains("@") || phone.getText().isBlank())
                throw new IllegalArgumentException("Enter a full name, valid email address and phone number.");
            if(birth.getValue()==null || birth.getValue().isAfter(LocalDate.now().minusYears(18)))
                throw new IllegalArgumentException("Enter a date of birth for a customer aged 18 or older.");
            Customer customer=new Customer();customer.setStaffID(staff);customer.setFullNames(name.getText().trim());
            customer.setEmailAddress(email.getText().trim());customer.setContactNo(phone.getText().trim());customer.setDateOfBirth(birth.getValue());
            if(!service.addCustomer(customer))throw new IllegalArgumentException("The customer could not be added. Check the details and retry.");
            onSaved.accept(customer);return true;
        });
    }
}
