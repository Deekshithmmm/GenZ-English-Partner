const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailError(email: string): string | null {
  if (!email.trim()) {
    return 'Enter your email.';
  }
  return EMAIL.test(email.trim()) ? null : "That email doesn't look right. Check it and try again.";
}

export function newPasswordError(password: string): string | null {
  if (!password) {
    return 'Choose a password.';
  }
  return password.length >= 8 ? null : 'Use at least 8 characters for your password.';
}

export function nameError(name: string): string | null {
  if (!name.trim()) {
    return 'Tell us what to call you.';
  }
  return name.trim().length <= 80 ? null : 'Keep your name under 80 characters.';
}
