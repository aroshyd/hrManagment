package uz.raximov.demo.service;

import org.omg.CORBA.PUBLIC_MEMBER;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.raximov.demo.component.Checker;
import uz.raximov.demo.component.MailSender;
import uz.raximov.demo.entity.Company;
import uz.raximov.demo.entity.Role;
import uz.raximov.demo.entity.Turniket;
import uz.raximov.demo.entity.User;
import uz.raximov.demo.enums.RoleName;
import uz.raximov.demo.payload.TurniketDto;
import uz.raximov.demo.repository.CompanyRepository;
import uz.raximov.demo.repository.TurniketRepository;
import uz.raximov.demo.response.ApiResponse;
import uz.raximov.demo.security.JwtProvider;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TurniketService {

    @Autowired
    TurniketRepository turniketRepository;

    @Autowired
    Checker checker;

    @Autowired
    UserService userService;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    MailSender mailSender;

    @Autowired
    JwtProvider jwtProvider;

    public ApiResponse add(TurniketDto turniketDto, HttpServletRequest httpServletRequest) throws MessagingException {
        ApiResponse response = userService.getByEmail(turniketDto.getOwnerEmail(), httpServletRequest);
        if (!response.isStatus())
            return response;

        User user = (User) response.getObject();
        Optional<Company> optionalCompany = companyRepository.findById(turniketDto.getCompanyId());
        if (!optionalCompany.isPresent())
            return new ApiResponse("Company not found!", false);

        Turniket turniket = new Turniket();
        turniket.setCompany(optionalCompany.get());
        turniket.setOwner(user);
        assert !turniketDto.isEnabled();
        turniket.setEnabled(turniketDto.isEnabled());
        Turniket saved = turniketRepository.save(turniket);
        mailSender.mailTextTurniketStatus(saved.getOwner().getEmail(), saved.isEnabled());
        return new ApiResponse("Turniket succesfully created!", true);
    }

    //FAQATGINA TURNIKETNING HUQUQINI O'ZGARTIRISH MUMKIN
    public ApiResponse edit(String number, TurniketDto turniketDto, HttpServletRequest httpServletRequest) throws MessagingException {
        Optional<Turniket> optionalTurniket = turniketRepository.findByNumber(number);
        if (!optionalTurniket.isPresent())
            return new ApiResponse("Turniket not found!", false);

        Turniket turniket = optionalTurniket.get();
        turniket.setEnabled(turniketDto.isEnabled());
        Turniket saved = turniketRepository.save(turniket);
        mailSender.mailTextTurniketStatus(saved.getOwner().getEmail(), saved.isEnabled());
        return new ApiResponse("Turniket succesfully edited!", true);
    }

    public ApiResponse delete(String number, HttpServletRequest httpServletRequest){
        Optional<Turniket> optionalTurniket = turniketRepository.findByNumber(number);
        if (!optionalTurniket.isPresent())
                return new ApiResponse("Turniket not found!", false);


        Set<Role> roles = optionalTurniket.get().getOwner().getRoles();
        String role = null;
        for (Role roleName : roles) {
            role = roleName.getName().name();
            break;
        }
        boolean check = checker.check(httpServletRequest, role);

        if (!check)
            return new ApiResponse("You have no such right!", false);

        turniketRepository.delete(optionalTurniket.get());
        return new ApiResponse("Turniket deleted!", true);
    }

    public ApiResponse getByNumber(HttpServletRequest httpServletRequest, String number){
        String autorization = httpServletRequest.getHeader("Autorization");
        String username = jwtProvider.getUsernameFromToken(autorization.substring(7));
        ApiResponse byEmail = userService.getByEmail(username, httpServletRequest);
        if (!byEmail.isStatus())
            return byEmail;

        Optional<Turniket> byNumber = turniketRepository.findByNumber(number);
        if (!byNumber.isPresent())
            return new ApiResponse("Turniket not found!", false);

        Set<Role> roles = byNumber.get().getOwner().getRoles();
        String role = null;
        for (Role roleName : roles) {
            role = roleName.getName().name();
            break;
        }
        boolean check = checker.check(httpServletRequest, role);

        if (byNumber.get().getOwner().getEmail().equals(username) || check){
            return new ApiResponse("Turniket", true, byNumber.get());
        }
        return new ApiResponse("You have no such right!", false);
    }

    public ApiResponse getAll(HttpServletRequest httpServletRequest){
        String autorization = httpServletRequest.getHeader("Autorization");
        String username = jwtProvider.getUsernameFromToken(autorization.substring(7));
        ApiResponse byEmail = userService.getByEmail(username, httpServletRequest);
        if (!byEmail.isStatus())
            return byEmail;

        User user = (User) byEmail.getObject();
        Set<Role> roles = user.getRoles();
        String role = RoleName.ROLE_STAFF.name();
        for (Role roleName : roles) {
            role = roleName.getName().name();
            break;
        }

        if (role.equals(RoleName.ROLE_DIRECTOR.name()))
            return new ApiResponse("Turniket List",true, turniketRepository.findAll());



    }
}