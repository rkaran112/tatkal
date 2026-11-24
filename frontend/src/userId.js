// Each browser tab gets its own random user id, kept in sessionStorage.
// Open the page in two tabs and you have two different people competing for seats.

export function getUserId() {
  let id = sessionStorage.getItem("userId");
  if (!id) {
    id = "user-" + Math.random().toString(36).slice(2, 6);
    sessionStorage.setItem("userId", id);
  }
  return id;
}
