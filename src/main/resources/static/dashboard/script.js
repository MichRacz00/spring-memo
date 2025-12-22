
const logoutButton = document.getElementById("logoutButton");
const changeBackgroundButton = document.getElementById("changeBackground");

const fileInput = document.getElementById('fileInput');

logoutButton.addEventListener("click", async () => {
    window.location.href = "/logout";
});

changeBackgroundButton.addEventListener("click", () => {
    fileInput.click();
});

fileInput.addEventListener("change", async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("image", file);

    try {
        await fetch('/ui/background', {
            method: 'POST',
            body: formData
        });
    } catch (error) {
        console.error("Error uploading file:", error);
    }

    await setBackground();
});

async function setBackground() {
    try {
        const response = await fetch("/ui/background", {
            method: "GET"
        });

        if (response.ok) {
            const imageUrl = await response.text();

            if (imageUrl) {
                document.body.style.backgroundImage = `url('${imageUrl}')`;
                document.body.style.backgroundSize = "cover";
                document.body.style.backgroundPosition = "center";
                document.body.style.backgroundRepeat = "no-repeat";
            }
        }
    } catch (error) {
        console.error("Error setting background:", error);
    }
}

window.addEventListener("load", () => {
    setBackground();
});