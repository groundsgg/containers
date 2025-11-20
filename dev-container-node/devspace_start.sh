#!/bin/bash
set +e

echo "Installing PNPM Dependencies"
pnpm install --reporter=silent

COLOR_BLUE="\033[0;94m"
COLOR_GREEN="\033[0;92m"
COLOR_RESET="\033[0m"

echo -e "


Welcome to your ${DEVSPACE_NAME} development container!

This is how you can work with it:
- Files will be synchronized between your local machine and this container
- With some applications database migration and seeding is needed. Run \`${COLOR_GREEN}pnpm db:migrate${COLOR_RESET}\` and \`${COLOR_GREEN}pnpm db:seed${COLOR_RESET}\` to run the migrations and seed the database. 
  Look in the scripts section of the \`${COLOR_GREEN}package.json${COLOR_RESET}\` file for more information.
- Run \`${COLOR_GREEN}pnpm start${COLOR_RESET}\` or the script defined in the \`${COLOR_GREEN}package.json${COLOR_RESET}\` file to start the application"

if [[ -n "$INGRESS_URL" ]]; then
  echo -e "- Access ${DEVSPACE_NAME} at ${INGRESS_URL}"
fi

echo ""

# Set terminal prompt
export PS1="\[${COLOR_BLUE}\]${DEVSPACE_NAME}\[${COLOR_RESET}\] ./\W \[${COLOR_BLUE}\]\\$\[${COLOR_RESET}\] "
if [ -z "$BASH" ]; then export PS1="$ "; fi

# Open shell
bash --norc
