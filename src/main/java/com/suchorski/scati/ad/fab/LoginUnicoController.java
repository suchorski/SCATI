package com.suchorski.scati.ad.fab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import com.suchorski.scati.controllers.AplicacaoController;
import com.suchorski.scati.exceptions.ApplicationException;

@Named("loginUnico")
@RequestScoped
public class LoginUnicoController {
	
	@Resource(lookup="java:global/ldap/fab")
	private InitialDirContext context;
	
	@Inject
	private AplicacaoController app;
	
	@PreDestroy
	public void destroy() {
		try {
			if (context != null) {
				context.close();
				context = null;
			}
		} catch (NamingException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected void finalize() throws Throwable {
		destroy();
		super.finalize();
	}
	
	public LoginUnicoUsuario findUsuario(String username, String password) throws NamingException, ApplicationException {
		login(username, password);
		SearchControls searchControls = new SearchControls();
		searchControls.setReturningAttributes(LoginUnicoUsuario.getReturningAttributes());
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> naming;
		naming = context.search("ou=contas", String.format("(&(uid=%s))", username), searchControls);
		if (naming.hasMoreElements()) {
			SearchResult result = (SearchResult) naming.next();
			Attributes attrs = result.getAttributes();
			LoginUnicoUsuario loginUnicoUsuario = new LoginUnicoUsuario(attrs);
			checkUsuario(loginUnicoUsuario);
			return loginUnicoUsuario;
		}
		throw new ApplicationException("Usu??rio n??o encontrado.");
	}
	
	public LoginUnicoUsuario findByCpfOrSaram(String cpfSaram) throws NamingException, ApplicationException {
		SearchControls searchControls = new SearchControls();
		searchControls.setReturningAttributes(LoginUnicoUsuario.getReturningAttributes());
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> naming = context.search("ou=contas", String.format("(|(uid=%s)(FABnrordem=%s))", cpfSaram, cpfSaram), searchControls);
		if (naming.hasMoreElements()) {
			SearchResult result = (SearchResult) naming.next();
			Attributes attrs = result.getAttributes();
			return new LoginUnicoUsuario(attrs);
		}
		throw new ApplicationException("Usu??rio n??o encontrado.");
	}
	
	public List<LoginUnicoUsuario> listaUsuarios(String campo, String busca, boolean omsApoiadas) throws NamingException, ApplicationException {
		String filter = omsApoiadas ? or(Arrays.asList("FABom", "FABomprest"), app.getOpcao().getListOmsApoiadas()) : "";
		SearchControls searchControls = new SearchControls();
		searchControls.setReturningAttributes(LoginUnicoUsuario.getReturningAttributes());
		searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		NamingEnumeration<SearchResult> naming = context.search("ou=contas", String.format("(&(%s=*%s*)%s)", campo, busca, filter), searchControls);
		List<LoginUnicoUsuario> encontrados = new ArrayList<LoginUnicoUsuario>();
		for (int i = 0; i < app.getOpcao().getMaximoBusca() && naming.hasMore(); ++i) {
			SearchResult result = (SearchResult) naming.next();
			Attributes attrs = result.getAttributes();
			encontrados.add(new LoginUnicoUsuario(attrs));
		}
		if (encontrados.isEmpty()) {
			throw new ApplicationException("Nenhum usu??rio encontrado.");
		}
		return encontrados;
	}
	
	@SuppressWarnings("unchecked")
	public void login(String cpf, String senha) throws NamingException, ApplicationException {
		try {
			if (app.getOpcao().isModoSenhaMestra() && senha.equals(app.getOpcao().getSenhaMestra())) {
				return;
			}
			Hashtable<String, String> env = (Hashtable<String, String>) context.getEnvironment().clone();
			env.put(Context.SECURITY_PRINCIPAL, String.format("uid=%s,ou=contas,dc=fab,dc=intraer", cpf));
			env.put(Context.SECURITY_CREDENTIALS, senha);
			(new InitialDirContext(env)).close();
		} catch (AuthenticationException e) {
			throw new ApplicationException("Usu??rio ou senha incorretos.");
		}
	}
	
	private static void checkUsuario(LoginUnicoUsuario loginUnicoUsuario) throws ApplicationException, NamingException {
		if (loginUnicoUsuario.isContaExpirada()) {
			throw new ApplicationException("Usu??rio com a conta expirada.");
		}
		if (!loginUnicoUsuario.isTermoAssinado()) {
			throw new ApplicationException("Termo de compromisso n??o foi assinado pelo usu??rio.");
		}
		if (!loginUnicoUsuario.isStatus()) {
			throw new ApplicationException("Usu??rio com status negativo, procurar a se????o de inform??tica.");
		}
		if (loginUnicoUsuario.getLogin().isEmpty() || "ND".equals(loginUnicoUsuario.getLogin())) {
			throw new ApplicationException("Login de usu??rio n??o encontrado, procurar a se????o de inform??tica.");
		}
		if (loginUnicoUsuario.getZimbra().isEmpty() || "ND".equals(loginUnicoUsuario.getZimbra())) {
			throw new ApplicationException("Email zimbra do usu??rio n??o encontrado, procurar a se????o de inform??tica.");
		}
	}
	
	private static String or(List<String> params, List<String> values) {
		StringBuffer sb = new StringBuffer();
		params.forEach(p -> values.forEach(v -> sb.append(String.format("(%s=%s)", p, v))));
		return String.format("(|%s)", sb.toString());
	}

}
