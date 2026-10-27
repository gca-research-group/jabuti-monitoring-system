export interface User {
  id?: number;
  name: string;
  email: string;
  photo: string;
  accessToken?: string;
  refreshToken?: string;
  isAuthenticated: boolean;
  status: boolean;
}

/** Management responses contain no credentials or authentication state. */
export interface UserResponse {
  id: number;
  name: string;
  email: string;
  photo: string | null;
  status: boolean;
  remarks: string | null;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  password: string;
  photo?: string | null;
  status?: boolean;
  remarks?: string | null;
}

export interface UpdateUserRequest {
  name?: string;
  email?: string;
  password?: string;
  photo?: string | null;
  status?: boolean;
  remarks?: string | null;
}
