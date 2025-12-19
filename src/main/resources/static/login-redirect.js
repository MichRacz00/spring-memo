
const loginButton = document.getElementById("loginButton");

loginButton.addEventListener("click", async () => {
    window.location.href = "/oauth2/authorization/azure";
});