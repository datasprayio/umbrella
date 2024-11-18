
install: install-api install-server


install-integrations: install-integration-tomcat
install-integration-tomcat:
	@echo "Installing integrations..."
	@source "$(HOME)/.sdkman/bin/sdkman-init.sh" \
		&& cd umbrella-integrations/umbrella-tomcat \
		&& sdk env install \
		&& mvn clean install

install-server:
	@echo "Installing server..."
	cd umbrella-server && dst install
