package de.shell;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.shell.Availability;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.*;
import org.springframework.shell.table.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class ShellApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShellApplication.class, args);
	}
}

@Data
@AllArgsConstructor
class Product {
	private String code;
	private String description;
}

@Data
@AllArgsConstructor
class User {
	private String username;
	private String password;
}

@Service
class UserService {
	private Map<String, User> userIdToUser;
	private User user;

	@PostConstruct
	public void setUp() {
		userIdToUser = new HashMap<>();
		userIdToUser.put("user1", new User("1", "1"));
		userIdToUser.put("user2", new User("2", "2"));
		userIdToUser.put("user3", new User("3", "3"));
	}

	public String login(String username, String password) {
		User user = userIdToUser.get(username);
		if (user == null) {
			return "Not logged in";
		}
		if (user.getPassword().equals(password)) {
			this.user = user;
			return "Logged in";
		} else {
			return "Not logged in";
		}
	}

	public String logout() {
		return "Logged out";
	}

	public boolean isUserLoggedIn() {
		return this.user != null;
	}

	public User getCurrentUser() {
		return user;
	}
}

//@Component
//class CustomPromptProvider implements PromptProvider {
//	private final UserService userService;
//
//	@Autowired
//	CustomPromptProvider(UserService userService) {
//		this.userService = userService;
//	}
//
//	@Override
//	public AttributedString getPrompt() {
//		String username = userService.getCurrentUser().getUsername();
//		String logged = ofNullable(userService.getCurrentUser())
//				.map(User::getUsername)
//				.orElse("Not logged in");
//		return new AttributedString(logged);
//	}
//}

@ShellComponent
class AuthCommands {
	private final UserService userService;

	@Autowired
	AuthCommands(UserService userService) {
		this.userService = userService;
	}

	@ShellMethod("Allows to login.")
	public String login(String username, String password) {
		return userService.login(username, password);
	}

	@ShellMethod("Allows to logout.")
	public String logout() {
		return userService.logout();
	}

	@ShellMethodAvailability("logout")
	public Availability logoutAvailability() {
		return userService.isUserLoggedIn()
				? Availability.available()
				: Availability.unavailable("You are not logged in");
	}
}

@Component
class ProductService {
	private List<Product> products = new ArrayList<>();

	@PostConstruct
	public void postConstruct() {
		products.add(new Product("code1", "description1"));
		products.add(new Product("code2", "description2"));
	}

	public List<Product> findProduct(String text) {
		return products.stream()
				.filter(product -> 	product.getCode().contains(text) ||
							product.getDescription().contains(text))
				.collect(Collectors.toList());
	}
}

@Component
class ProductConverter implements Converter<String, Product> {
	@Override
	public Product convert(String productString) {
		String[] split = productString.split(",");
		String code = split[0];
		String description = split[1];
		return new Product(code, description);
	}
}

@ShellComponent
class ProductCommands {
	private final ProductService productService;

	@Autowired
	ProductCommands(ProductService productService) {
		this.productService = productService;
	}

	@ShellMethod("Allows to search products")
	public Table searchProduct(@ShellOption(valueProvider = ProductsValueProvider.class) String text) {
		List<Product> product = productService.findProduct(text);
		TableModel tableModel = new BeanListTableModel<>(product, "code", "description");
		return new TableBuilder(tableModel)
				.addFullBorder(BorderStyle.fancy_heavy)
				.build();
	}

	@ShellMethod("Allows to add to card")
	public String addToCard(Product product) {
		return "Success";
	}
}

@Component
class ProductsValueProvider implements ValueProvider {
	private final ProductService productService;

	@Autowired
	ProductsValueProvider(ProductService productService) {
		this.productService = productService;
	}

	@Override
	public boolean supports(MethodParameter methodParameter, CompletionContext completionContext) {
		return methodParameter.getParameterType().isAssignableFrom(String.class);
	}

	@Override
	public List<CompletionProposal> complete(MethodParameter methodParameter, CompletionContext completionContext, String[] strings) {
		String currentWordUpToCursor = completionContext.currentWordUpToCursor();
		return productService.findProduct(currentWordUpToCursor)
				.stream()
				.map(Product::getCode)
				.map(CompletionProposal::new)
				.collect(Collectors.toList());
	}
}
